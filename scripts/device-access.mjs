// Administrative provisioning only. No message or conversation permissions.
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export function buildDeviceRegistration(userId, label, now = new Date()) {
  if (typeof userId !== 'string' || !uuidPattern.test(userId)) {
    throw new Error('Expected a dedicated device account UUID.');
  }
  if (typeof label !== 'string' || !label.trim() || label.trim().length > 110) {
    throw new Error('Invalid device label.');
  }
  return {
    rowId: userId,
    data: {
      deviceId: userId,
      name: label.trim(),
      platform: 'android',
      lastSeen: now.toISOString(),
    },
    permissions: ['read("user:' + userId + '")'],
  };
}
