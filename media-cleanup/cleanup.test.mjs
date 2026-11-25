import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, randomUUID } from 'node:crypto';
import { createServer, request } from 'node:http';
import { cert, initializeApp, deleteApp } from 'firebase-admin/app';
import { initializeFirestore } from 'firebase-admin/firestore';
import { removedAssets } from './assets.mjs';
import { cleanUnreferencedMedia, ingestMediaRemovals, queueRemovals } from './cleanup.mjs';

const emulatorHost = process.env.FIRESTORE_EMULATOR_HOST ?? '';
const localEmulator = /^(127\.0\.0\.1|localhost):\d+$/.test(emulatorHost);
let proxy;
let privateKey;

before(async () => {
  if (!localEmulator) return;
  privateKey = generateKeyPairSync('rsa', {
    modulusLength: 2048,
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' }
  }).privateKey;
  const [, port] = emulatorHost.split(':');
  // Preserve emulator-admin authentication on loopback while the REST client uses a test certificate.
  proxy = createServer((incoming, response) => {
    const upstream = request({
      hostname: '127.0.0.1', port, path: incoming.url, method: incoming.method,
      headers: { ...incoming.headers, authorization: 'Bearer owner' },
    }, reply => {
      response.writeHead(reply.statusCode, reply.headers);
      reply.pipe(response);
    });
    upstream.on('error', () => { response.writeHead(502); response.end(); });
    incoming.pipe(upstream);
  });
  await new Promise(resolve => proxy.listen(0, '127.0.0.1', resolve));
  process.env.FIRESTORE_EMULATOR_HOST = `127.0.0.1:${proxy.address().port}`;
});

after(async () => {
  if (!proxy) return;
  process.env.FIRESTORE_EMULATOR_HOST = emulatorHost;
  proxy.closeAllConnections();
  await new Promise(resolve => proxy.close(resolve));
});

function testApp(id) {
  return initializeApp({ projectId: id, credential: cert({
    projectId: id, clientEmail: `test@${id}.iam.gserviceaccount.com`, privateKey,
  }) }, id);
}

test('the Edge worker consumes a removal record and preserves the timestamp when queuing',
  { skip: !localEmulator }, async () => {
    const unique = randomUUID();
    const app = testApp(`demo-media-${unique.slice(0, 8)}`);
    const db = initializeFirestore(app, { preferRest: true });
    const publicId = `taskly/alice/${unique}`;
    const attachment = {
      publicId,
      resourceType: 'image',
      url: `https://res.cloudinary.com/lrpsglzl/image/upload/v42/${publicId}.jpg`,
    };
    const data = { senderId: 'alice', attachments: [attachment] };
    const [asset] = removedAssets(data);
    const receipt = db.collection('tasklyMediaRemovals').doc(unique);
    const queued = db.collection('mediaCleanupQueue').doc(asset.key);
    const timestamp = Date.now();
    try {
      await receipt.set({ ...data, conversationId: 'test', messageId: unique, createdAt: new Date(timestamp) });
      await ingestMediaRemovals(db);
      assert.equal((await queued.get()).get('publicId'), publicId);
      assert.equal((await queued.get()).get('nextCheckAt').toMillis(), timestamp + 600_000);
      assert.equal((await receipt.get()).exists, false);
    } finally {
      await deleteApp(app);
    }
  });

test('Firestore sweep retains a reforward, saved message and playlist track until the final reference is gone',
  { skip: !localEmulator }, async () => {
    const name = `demo-media-${randomUUID().slice(0, 8)}`;
    const app = testApp(name);
    const db = initializeFirestore(app, { preferRest: true });
    const url = 'https://res.cloudinary.com/lrpsglzl/video/upload/v42/taskly/alice/audio.mp3';
    const attachment = { publicId: 'taskly/alice/audio', resourceType: 'video', url };
    const [candidate] = removedAssets({ senderId: 'alice', attachments: [attachment] });
    let stamp = Date.now();
    const calls = [];
    const lease = db.doc('mediaCleanupControl/lease');
    const queued = db.collection('mediaCleanupQueue').doc(candidate.key);
    const forward = db.doc('conversations/other/messages/forward');
    const saved = db.doc('users/bob/saved/copy');
    const track = db.doc('users/bob/playlists/favorites/tracks/track');
    const run = () => cleanUnreferencedMedia(db, async (item) => {
      assert.equal((await lease.get()).exists, true);
      calls.push(item.publicId);
      return 'gone';
    }, { now: () => stamp });
    try {
      await queueRemovals(db, [candidate, candidate], stamp - 600_000);
      await forward.set({ attachments: [attachment] });
      await saved.set({ attachments: [attachment] });
      await track.set({ url });
      for (const reference of [forward, saved, track]) {
        await run();
        assert.deepEqual(calls, []);
        assert.equal((await lease.get()).exists, false);
        await reference.delete();
        stamp += 3_600_001;
      }
      await cleanUnreferencedMedia(db, async () => { throw new Error('offline'); }, { now: () => stamp });
      assert.equal((await queued.get()).get('result'), undefined);
      assert.equal((await lease.get()).exists, false);
      stamp += 3_600_001;
      await run();
      assert.deepEqual(calls, [candidate.publicId]);
      assert.equal((await queued.get()).get('result'), 'gone');
      await queueRemovals(db, [candidate], stamp - 600_000);
      await run();
      assert.equal(calls.length, 1);
    } finally {
      await deleteApp(app);
    }
  });
