import { createHash } from 'node:crypto';
import { Buffer } from 'node:buffer';

const cloud = 'lrpsglzl';
const types = new Set(['image', 'video', 'raw']);

function deliveryPath(value, allowHttp = false) {
  try {
    const url = new URL(value);
    if ((url.protocol !== 'https:' && !(allowHttp && url.protocol === 'http:')) ||
        url.hostname !== 'res.cloudinary.com' ||
        url.username || url.password || url.port) return null;
    const [, environment, resourceType, delivery, ...path] = decodeURIComponent(url.pathname).split('/');
    if (environment !== cloud || !types.has(resourceType) || delivery !== 'upload') return null;
    return { resourceType, path: path.join('/') };
  } catch {
    return null;
  }
}

function originalAsset(attachment, senderId) {
  const { publicId, resourceType, url } = attachment ?? {};
  if (typeof senderId !== 'string' || !/^[^/]+$/.test(senderId) ||
      typeof publicId !== 'string' || !publicId || publicId.length > 255 || /[\x00-\x1f\x7f]/.test(publicId) ||
      !types.has(resourceType)) return null;
  if (publicId.startsWith('taskly/') && !publicId.startsWith(`taskly/${senderId}/`)) return null;
  const delivery = deliveryPath(url);
  if (delivery?.resourceType !== resourceType) return null;
  const match = /^v(\d+)\/(.+)$/.exec(delivery.path);
  if (!match) return null;
  const [, version, file] = match;
  if (file !== publicId && (resourceType === 'raw' || file.replace(/\.[^/.]+$/, '') !== publicId)) return null;
  const key = createHash('sha256').update(JSON.stringify([resourceType, publicId, version, senderId])).digest('hex');
  return { key, publicId, resourceType, version, senderId };
}

export function removedAssets(before, after) {
  const previous = Array.isArray(before?.attachments) ? before.attachments : [];
  const current = Array.isArray(after?.attachments) ? after.attachments : [];
  const remaining = new Set(current.map(file => originalAsset(file, before?.senderId)?.key));
  const removed = new Map();
  for (const file of previous) {
    const asset = originalAsset(file, before?.senderId);
    if (asset && !remaining.has(asset.key)) removed.set(asset.key, asset);
  }
  return [...removed.values()];
}

export function markReferencedAssets(value, pendingIds, referenced) {
  if (typeof value === 'string') {
    if (pendingIds.has(value)) referenced.add(value);
    const scanUrl = url => {
      const path = deliveryPath(url.startsWith('//') ? `https:${url}` : url, true)?.path;
      if (!path) return;
      const withoutFormat = path.replace(/\.[^/.]+$/, '');
      for (const id of pendingIds) {
        if (path === id || path.endsWith(`/${id}`) || withoutFormat === id || withoutFormat.endsWith(`/${id}`)) {
          referenced.add(id);
        }
      }
    };
    scanUrl(value.trim());
    for (const match of value.matchAll(/(?:https?:)?\/\/res\.cloudinary\.com\/[^\s<>"'\x60\])}]+/gi)) {
      scanUrl(match[0].replace(/[.,!?;]+$/, ''));
    }
  } else if (Array.isArray(value) || (value && Object.getPrototypeOf(value) === Object.prototype)) {
    for (const child of Object.values(value)) markReferencedAssets(child, pendingIds, referenced);
  }
}

export function cloudinaryAdmin(credentials, request = fetch) {
  let config;
  try {
    config = new URL(credentials);
  } catch {
    throw new Error('Invalid Cloudinary service configuration');
  }
  if (config.protocol !== 'cloudinary:' || config.hostname !== cloud || !config.username || !config.password) {
    throw new Error('Invalid Cloudinary service configuration');
  }
  const key = decodeURIComponent(config.username);
  const secret = decodeURIComponent(config.password);
  const base = `https://api.cloudinary.com/v1_1/${cloud}`;
  const authorization = `Basic ${Buffer.from(`${key}:${secret}`).toString('base64')}`;

  return async function destroyAsset(candidate) {
    const { resourceType, publicId, version, senderId } = candidate;
    const response = await request(`${base}/resources/${resourceType}/upload/${encodeURIComponent(publicId)}`, {
      headers: { authorization },
      signal: AbortSignal.timeout(5_000),
      redirect: 'error',
    });
    if (response.status === 404) return 'gone';
    if (!response.ok) throw new Error(`Cloudinary asset lookup failed (${response.status})`);
    const asset = await response.json();
    const uploaderFolder = `taskly/${senderId}`;
    const owned = publicId.startsWith(`${uploaderFolder}/`) || asset.asset_folder === uploaderFolder;
    if (!owned || asset.public_id !== publicId || asset.resource_type !== resourceType || asset.type !== 'upload' ||
        String(asset.version) !== version || !/^[a-f0-9]{32}$/i.test(asset.asset_id)) return 'changed';
    const parameters = new URLSearchParams({ 'asset_ids[]': asset.asset_id, invalidate: 'true' });
    const result = await request(`${base}/resources?${parameters}`, {
      method: 'DELETE',
      headers: { authorization },
      signal: AbortSignal.timeout(5_000),
      redirect: 'error',
    });
    if (!result.ok) throw new Error(`Cloudinary asset deletion failed (${result.status})`);
    const { deleted } = await result.json();
    const outcome = deleted?.[asset.asset_id] ?? deleted?.[publicId];
    if (outcome === 'deleted' || outcome === 'not_found') return 'gone';
    throw new Error('Cloudinary did not confirm asset deletion');
  };
}
