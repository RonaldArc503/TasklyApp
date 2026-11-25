import { timingSafeEqual } from 'node:crypto';
import { Buffer } from 'node:buffer';

export function createMediaCleanupHandler(secret, run) {
  return async request => {
    if (request.method !== 'POST') return new Response(null, { status: 405 });
    const expected = Buffer.from(secret ?? '');
    const provided = Buffer.from(request.headers.get('x-taskly-cron-secret') ?? '');
    if (expected.length < 32 || provided.length !== expected.length || !timingSafeEqual(expected, provided)) {
      return new Response(null, { status: 401 });
    }
    try {
      await run();
      return new Response(null, { status: 204 });
    } catch {
      console.error('Taskly media cleanup did not complete');
      return new Response(null, { status: 503 });
    }
  };
}
