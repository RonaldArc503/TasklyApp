import test from 'node:test';
import assert from 'node:assert/strict';
import { createMediaCleanupHandler } from './edge.mjs';

test('only an authenticated Cron POST can run ingestion and cleanup', async () => {
  const secret = 'x'.repeat(32);
  let calls = 0;
  const handler = createMediaCleanupHandler(secret, async () => { calls++; });
  const invoke = (method, header = '') => handler(new Request('https://example.test', {
    method, headers: { 'x-taskly-cron-secret': header },
  }));
  assert.equal((await invoke('GET', secret)).status, 405);
  assert.equal((await invoke('POST', 'y'.repeat(32))).status, 401);
  assert.equal((await invoke('POST')).status, 401);
  assert.equal(calls, 0);
  assert.equal((await invoke('POST', secret)).status, 204);
  assert.equal(calls, 1);
});
