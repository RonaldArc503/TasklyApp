import { randomUUID } from 'node:crypto';
import { markReferencedAssets, removedAssets } from './assets.mjs';

const leaseLength = 45_000;
const providerWindow = 15_000;
const retryAfter = 60 * 60 * 1000;

export async function queueRemovals(db, assets, now = Date.now()) {
  await Promise.all(assets.map(async ({ key, ...asset }) => {
    try {
      await db.collection('mediaCleanupQueue').doc(key).create({
        ...asset, nextCheckAt: new Date(now + 10 * 60 * 1000),
      });
    } catch (error) {
      if (error.code !== 6 && error.code !== 'already-exists') throw error;
    }
  }));
}

export async function ingestMediaRemovals(db) {
  const receipts = await db.collection('tasklyMediaRemovals').limit(30).get();
  for (const receipt of receipts.docs) {
    const recordedAt = receipt.get('createdAt');
    await queueRemovals(db, removedAssets(receipt.data()), recordedAt.toMillis());
    await receipt.ref.delete();
  }
}

async function findReferences(db, pendingIds, deadline, now) {
  const referenced = new Set();
  const queries = [
    db.collectionGroup('messages').select('attachments', 'text', 'replyTo'),
    db.collectionGroup('saved'),
    db.collectionGroup('tracks'),
    db.collectionGroup('playlists'),
    db.collectionGroup('musicFavorites'),
    db.collectionGroup('reminders'),
    db.collection('profiles').select('photoURL'),
    db.collection('conversations').select('members'),
  ];
  for (const query of queries) {
    if (now() >= deadline) throw new Error('Media reference scan exceeded its time budget');
    for await (const doc of query.stream()) {
      if (now() >= deadline) throw new Error('Media reference scan exceeded its time budget');
      markReferencedAssets(doc.data(), pendingIds, referenced);
      if (referenced.size === pendingIds.size) return referenced;
    }
  }
  return referenced;
}

export async function cleanUnreferencedMedia(db, destroyAsset, { now = Date.now, onFailure = () => {} } = {}) {
  const due = await db.collection('mediaCleanupQueue')
    .where('nextCheckAt', '<=', new Date(now())).orderBy('nextCheckAt').limit(10).get();
  if (due.empty) return;
  const lease = db.doc('mediaCleanupControl/lease');
  const owner = randomUUID();
  const until = await db.runTransaction(async transaction => {
    const current = await transaction.get(lease);
    if ((current.get('until')?.toMillis() ?? 0) > now()) return null;
    const expires = now() + leaseLength;
    transaction.set(lease, { owner, until: new Date(expires) });
    return expires;
  });
  if (until === null) return;

  try {
    // Firestore rules suspend writes that can add these references until the lease is released.
    const pending = new Set(due.docs.map(doc => doc.get('publicId')));
    const referenced = await findReferences(db, pending, until - providerWindow, now);
    const outcomes = await Promise.allSettled(due.docs.map(async doc => {
      const candidate = doc.data();
      if (referenced.has(candidate.publicId)) {
        await doc.ref.update({ nextCheckAt: new Date(now() + retryAfter) });
        return;
      }
      if (now() >= until - providerWindow) return;
      try {
        const result = await destroyAsset(candidate);
        const { nextCheckAt, ...identity } = candidate;
        await doc.ref.set({ ...identity, result, finishedAt: new Date(now()) });
      } catch {
        await doc.ref.update({ nextCheckAt: new Date(now() + retryAfter) });
        onFailure('Cloudinary cleanup could not complete; retry scheduled');
      }
    }));
    const failed = outcomes.find(outcome => outcome.status === 'rejected');
    if (failed) throw failed.reason;
  } finally {
    await db.runTransaction(async transaction => {
      const current = await transaction.get(lease);
      if (current.get('owner') === owner) transaction.delete(lease);
    });
  }
}
