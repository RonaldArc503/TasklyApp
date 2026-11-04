// Run against a local demo Firestore emulator only; see docs/MUSICA_LISTAS_OFFLINE_2026-11-03.md.
import { createRequire } from 'node:module';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
import path from 'node:path';

const require = createRequire(path.join(process.env.TASKLY_RULES_DEPS || process.cwd(), 'package.json'));
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, collection, setDoc, getDoc, getDocs, updateDoc, deleteDoc, writeBatch } = require('firebase/firestore');
const host = process.env.FIRESTORE_EMULATOR_HOST;
assert(host && /^(127\.0\.0\.1|localhost):\d+$/.test(host), 'Local emulator required');
const [hostname, port] = host.split(':');
const env = await initializeTestEnvironment({ projectId: 'demo-taskly-music',
  firestore: { host: hostname, port: Number(port), rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') } });

try {
  await env.clearFirestore();
  const alice = env.authenticatedContext('alice').firestore();
  const bob = env.authenticatedContext('bob').firestore();
  const guest = env.unauthenticatedContext().firestore();
  const listPath = 'users/alice/playlists/one';
  const token = 'a123456789abcdef0123456789abcdef';
  const sharePath = `sharedMusicPlaylists/${token}`;
  await setDoc(doc(alice, listPath), { userId: 'alice', name: 'Private list' });
  await setDoc(doc(alice, `${listPath}/tracks/song`), { mediaId: 'song', url: 'https://example.invalid/audio' });
  await assertFails(getDoc(doc(bob, listPath)));
  await assertFails(getDocs(collection(bob, `${listPath}/tracks`)));
  await assertFails(setDoc(doc(bob, sharePath), { ownerId: 'alice', playlistId: 'one' }));
  await assertFails(setDoc(doc(alice, sharePath), { ownerId: 'alice', playlistId: 'one' }));
  const batch = writeBatch(alice);
  batch.set(doc(alice, sharePath), { ownerId: 'alice', playlistId: 'one' });
  batch.update(doc(alice, listPath), { shareId: token });
  await assertSucceeds(batch.commit());
  await assertSucceeds(getDoc(doc(bob, sharePath)));
  await assertSucceeds(getDoc(doc(bob, listPath)));
  assert.equal((await assertSucceeds(getDocs(collection(bob, `${listPath}/tracks`)))).size, 1);
  await assertFails(getDocs(collection(bob, 'sharedMusicPlaylists')));
  await assertFails(getDocs(collection(bob, 'users/alice/playlists')));
  await assertFails(getDoc(doc(guest, sharePath)));
  await assertFails(getDoc(doc(guest, listPath)));
  await assertFails(updateDoc(doc(bob, listPath), { name: 'Hijacked' }));
  await assertFails(setDoc(doc(bob, `${listPath}/tracks/other`), { mediaId: 'other' }));
  await assertFails(deleteDoc(doc(bob, `${listPath}/tracks/song`)));
  await assertFails(updateDoc(doc(alice, sharePath), { playlistId: 'other' }));
  await assertFails(deleteDoc(doc(bob, sharePath)));
  await assertSucceeds(setDoc(doc(bob, `users/bob/playlists/shared_${token}`), {
    userId: 'bob', sharedOwnerId: 'alice', sharedPlaylistId: 'one', shareId: token
  }));
  await assertSucceeds(deleteDoc(doc(bob, `users/bob/playlists/shared_${token}`)));
  assert.equal((await getDoc(doc(alice, listPath))).data().name, 'Private list');
  await deleteDoc(doc(alice, sharePath));
  await assertFails(getDoc(doc(bob, listPath)));
  await assertFails(getDocs(collection(bob, `${listPath}/tracks`)));
  console.log('PASS: shared playlist rules (private/public reads, cross-user writes, saving and revocation).');
} finally { await env.cleanup(); }
