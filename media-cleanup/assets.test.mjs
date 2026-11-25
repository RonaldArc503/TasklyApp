import test from 'node:test';
import assert from 'node:assert/strict';
import { cloudinaryAdmin, markReferencedAssets, removedAssets } from './assets.mjs';

const file = {
  publicId: 'taskly/alice/photo',
  resourceType: 'image',
  url: 'https://res.cloudinary.com/lrpsglzl/image/upload/v42/taskly/alice/photo.jpg',
};

test('a message update queues only removed Cloudinary assets and deduplicates them', () => {
  const other = { ...file, publicId: 'taskly/alice/other', url: file.url.replace('photo.jpg', 'other.jpg') };
  const before = { senderId: 'alice', attachments: [file, file, other, { ...file, url: 'https://example.com/file' }] };
  const removed = removedAssets(before, { attachments: [other] });
  assert.equal(removed.length, 1);
  assert.deepEqual({ ...removed[0], key: undefined },
    { key: undefined, publicId: file.publicId, resourceType: 'image', version: '42', senderId: 'alice' });
  assert.deepEqual(removedAssets(before, before), []);
});

test('forwarded descriptors and saved or playlist URLs protect the original, including transformed links', () => {
  const versionless = 'https://res.cloudinary.com/lrpsglzl/image/upload/w_250/taskly/alice/photo.png';
  for (const document of [{ attachments: [file] }, { url: file.url }, { text: `[${versionless}]` }]) {
    const referenced = new Set();
    markReferencedAssets(document, new Set([file.publicId]), referenced);
    assert.deepEqual([...referenced], [file.publicId]);
  }
  const outside = new Set();
  markReferencedAssets({ url: file.url.replace('lrpsglzl', 'different-cloud') }, new Set([file.publicId]), outside);
  assert.equal(outside.size, 0);
});

test('Cloudinary deletion checks ownership and version and deletes only the immutable ID', async () => {
  const id = 'a'.repeat(32);
  const item = { asset_id: id, public_id: file.publicId, resource_type: 'image', type: 'upload', version: 42 };
  const candidate = removedAssets({ senderId: 'alice', attachments: [file] })[0];
  const calls = [];
  const response = (value) => new Response(JSON.stringify(value), { status: 200 });
  const destroy = cloudinaryAdmin('cloudinary://test-key:test-secret@lrpsglzl', async (url, options) => {
    calls.push({ url, options });
    return response(options.method === 'DELETE' ? { deleted: { [id]: 'deleted' } } : item);
  });
  assert.equal(await destroy({ ...candidate, senderId: 'mallory' }), 'changed');
  assert.equal(await destroy({ ...candidate, version: '41' }), 'changed');
  assert.equal(calls.length, 2);
  assert.equal(await destroy(candidate), 'gone');
  const endpoint = new URL(calls[3].url);
  assert.equal(endpoint.pathname, '/v1_1/lrpsglzl/resources');
  assert.deepEqual(endpoint.searchParams.getAll('asset_ids[]'), [id]);
  assert.equal(endpoint.searchParams.get('invalidate'), 'true');
  assert.equal(calls[3].options.headers.authorization, calls[2].options.headers.authorization);
});
