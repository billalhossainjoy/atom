// Device metadata only. Never apply these helpers to messages or conversations.
const validId = /^[A-Za-z0-9][A-Za-z0-9._-]{0,35}$/;
function readPermission(id) {
  if (typeof id !== 'string' || !validId.test(id)) throw new Error('Invalid account ID.');
  return 'read("user:' + id + '")';
}

export function ownerDevicePermissions(row, deviceId, ownerId, action) {
  const device = readPermission(deviceId);
  const owner = readPermission(ownerId);
  if (device === owner || !['link', 'unlink'].includes(action)) throw new Error('Invalid ownership operation.');
  if (!row || row.$id !== deviceId || row.deviceId !== deviceId || !Array.isArray(row.$permissions)) {
    throw new Error('Unexpected device registration.');
  }
  const existing = new Set(row.$permissions);
  if (!existing.has(device) || [...existing].some(p => p !== device && p !== owner)) {
    throw new Error('Unexpected access permissions. Review manually.');
  }
  return action === 'link' ? [device, owner] : [device];
}
