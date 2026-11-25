import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import { ownerDevicePermissions } from './owner-device-access.mjs';

// Trusted administrator only. Device metadata, not message access.
const endpoint = 'https://sgp.cloud.appwrite.io/v1';
const project = '6b0600f60013dd532f05';
const database = '6b06f1f7003b65907da2';
const key = process.env.APPWRITE_ENROLLMENT_KEY;
if (!key || !stdin.isTTY || !stdout.isTTY) {
  throw new Error('Use a private administrator terminal with APPWRITE_ENROLLMENT_KEY.');
}
async function call(path, method = 'GET', body) {
  const response = await fetch(endpoint + path, {
    method, redirect: 'error', signal: AbortSignal.timeout(15000),
    headers: { 'X-Appwrite-Project': project, 'X-Appwrite-Key': key, 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (!response.ok) throw new Error('Appwrite HTTP ' + response.status);
  return response.json();
}
const rl = createInterface({ input: stdin, output: stdout });
try {
  const action = (await rl.question('Action (link/unlink): ')).trim();
  if (!['link', 'unlink'].includes(action)) throw new Error('Invalid action.');
  const device = (await rl.question('Dedicated device account ID: ')).trim();
  const owner = (await rl.question('Verified owner dashboard account ID: ')).trim();
  const validId = /^[A-Za-z0-9][A-Za-z0-9._-]{0,35}$/;
  if (!validId.test(device) || !validId.test(owner) || device === owner) throw new Error('Invalid IDs.');
  const deviceUser = await call('/users/' + encodeURIComponent(device));
  if (!deviceUser.name?.startsWith('Device: ') || deviceUser.$id !== device) {
    throw new Error('Not a dedicated device account.');
  }
  if (action === 'link') {
    const ownerUser = await call('/users/' + encodeURIComponent(owner));
    if (!deviceUser.status || !ownerUser.status || !ownerUser.emailVerification ||
        ownerUser.$id !== owner || ownerUser.name?.startsWith('Device: ')) {
      throw new Error('Expected active device and verified human owner accounts.');
    }
    console.log('Confirm that the device owner controls this verified dashboard account before proceeding.');
  }
  const path = '/tablesdb/' + database + '/tables/devices/rows/' + encodeURIComponent(device);
  const row = await call(path);
  const permissions = ownerDevicePermissions(row, device, owner, action);
  console.log('Device label:', row.name);
  const confirmation = await rl.question(
    action === 'link'
      ? 'Has the device owner explicitly requested this metadata link? Type APPROVED: '
      : 'Revoke this dashboard account metadata access? Type APPROVED: '
  );
  if (confirmation !== 'APPROVED') throw new Error('Cancelled.');
  // Recheck after the interactive prompt; run only one administrator operation at a time.
  const latest = await call(path);
  const next = ownerDevicePermissions(latest, device, owner, action);
  if (JSON.stringify(permissions) !== JSON.stringify(next)) throw new Error('Permissions changed; retry.');
  if (JSON.stringify(latest.$permissions) !== JSON.stringify(next)) {
    const updated = await call(path, 'PATCH', { permissions: next });
    if (updated.$id !== device ||
        JSON.stringify([...updated.$permissions].sort()) !== JSON.stringify([...next].sort())) {
      throw new Error('Unexpected update response. Review device metadata permissions.');
    }
  }
  console.log(action === 'link' ? 'Device metadata linked.' : 'Device metadata access revoked.');
  console.log('No SMS or conversation permissions changed.');
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  rl.close();
}
