# Limpieza de adjuntos de Taskly

Android registra los adjuntos retirados dentro de la misma operación de **Eliminar para todos**. Firestore verifica que los adjuntos venían del mensaje y que fueron retirados. La Edge Function `taskly-media-cleanup` consume esos registros y comprueba reenvíos, Guardados, listas, pistas, favoritos, recordatorios y fotos de perfiles o conversaciones. Si queda una referencia, conserva el archivo y vuelve a comprobarlo después de una hora. **Eliminar para mí** permanece local.

El servidor verifica el archivo, su versión y su pertenencia al espacio de carga del remitente antes de eliminarlo. Los borrados anteriores a la instalación del APK con esta integración y los borrados de clientes web sin registro no entran en la cola.

## Activación

1. Publica la Edge Function `taskly-media-cleanup` desde este checkout en el proyecto Supabase de Taskly. Usa `TASKLY_FIREBASE_SERVICE_ACCOUNT` y `TASKLY_CLOUDINARY_URL` como secretos de Edge Functions. Genera un secreto independiente de al menos 32 caracteres para `TASKLY_MEDIA_CRON_SECRET`.
2. Publica las reglas de Firestore de Android y web antes de distribuir el APK. La comprobación de la última referencia utiliza el bloqueo de escritura temporal que aplican esas reglas.
3. Guarda en Vault el valor de `TASKLY_MEDIA_CRON_SECRET` como `taskly_media_cron_secret` y la URL completa de la Edge Function como `taskly_media_cleanup_url`. Habilita Supabase Cron y `pg_net` y registra este trabajo.

Con Supabase CLI v3, el despliegue de la función usa `supabase functions deploy taskly-media-cleanup --remote=<proyecto> --use-api --no-verify-jwt --yes`.

```sql
select cron.schedule(
  'taskly-media-cleanup', '*/15 * * * *',
  $job$
  select net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name = 'taskly_media_cleanup_url'),
    headers := jsonb_build_object('x-taskly-cron-secret',
      (select decrypted_secret from vault.decrypted_secrets where name = 'taskly_media_cron_secret')),
    timeout_milliseconds := 55000
  );
  $job$
);
```

Activa `TASKLY_MEDIA_CLEANUP_ENABLED=true` en Supabase cuando las reglas con el bloqueo estén publicadas. Con la variable deshabilitada, el trabajo registra la cola y conserva los originales.

`npm test` verifica el cálculo de referencias y la solicitud a Cloudinary con respuestas simuladas. Con `FIRESTORE_EMULATOR_HOST` apuntando al emulador local, también ejecuta una prueba de la cola y de la última referencia.
