import { cert, initializeApp } from 'firebase-admin/app';
import { initializeFirestore } from 'firebase-admin/firestore';
import { cloudinaryAdmin } from '../../../media-cleanup/assets.mjs';
import { cleanUnreferencedMedia, ingestMediaRemovals } from '../../../media-cleanup/cleanup.mjs';
import { createMediaCleanupHandler } from '../../../media-cleanup/edge.mjs';

let firestore;
Deno.serve(createMediaCleanupHandler(Deno.env.get('TASKLY_MEDIA_CRON_SECRET'), async () => {
  if (!firestore) {
    const credential = JSON.parse(Deno.env.get('TASKLY_FIREBASE_SERVICE_ACCOUNT') ?? '{}');
    if (credential.project_id !== 'tasksv') throw new Error('Invalid Taskly service account');
    const app = initializeApp({ projectId: 'tasksv', credential: cert(credential) });
    firestore = initializeFirestore(app, { preferRest: true });
  }
  await ingestMediaRemovals(firestore);
  if (Deno.env.get('TASKLY_MEDIA_CLEANUP_ENABLED') === 'true') {
    await cleanUnreferencedMedia(firestore, cloudinaryAdmin(Deno.env.get('TASKLY_CLOUDINARY_URL')), {
      onFailure: console.warn,
    });
  }
}));
