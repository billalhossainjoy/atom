import { randomUUID } from 'node:crypto';
import { strict as assert } from 'node:assert';
import { buildDeviceRegistration } from './device-access.mjs';
import { ownerDevicePermissions } from './owner-device-access.mjs';

// Isolated test project only. Uses synthetic device metadata, never SMS.
// Admin credentials stay in this process. Test client requests use temporary sessions.
const endpoint = process.env.APPWRITE_TEST_ENDPOINT;
const project = process.env.APPWRITE_TEST_PROJECT_ID;
const database = process.env.APPWRITE_TEST_DATABASE_ID;
const key = process.env.APPWRITE_TEST_KEY;
if (!endpoint || !project || !database || !key) throw new Error('Configure the isolated test project first.');
if (project === '6b0600f60013dd532f05') throw new Error('Do not run against the production atom project.');
const url = new URL(endpoint);
if (url.protocol !== 'https:' || !url.hostname.endsWith('.cloud.appwrite.io') ||
    url.pathname !== '/v1' || url.username || url.password || url.search || url.hash) {
  throw new Error('Use a regional Appwrite Cloud HTTPS endpoint ending in /v1.');
}
const root = endpoint.replace(/\/$/, '');
async function request(path, {method = 'GET', body, admin = false, session} = {}) {
  const response = await fetch(root + path, {
    method, redirect: 'error', signal: AbortSignal.timeout(15000),
    headers: {
      'Content-Type': 'application/json', 'X-Appwrite-Project': project,
      ...(admin ? {'X-Appwrite-Key': key} : {}),
      ...(session ? {'X-Appwrite-Session': session} : {}),
    },
    ...(body === undefined ? {} : {body: JSON.stringify(body)}),
  });
  if (!response.ok) {
    const error = new Error('Appwrite HTTP ' + response.status);
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}
const admin = (path, method = 'GET', body) => request(path, {method, body, admin: true});
const rows = '/tablesdb/' + encodeURIComponent(database) + '/tables/devices/rows';
const users = [];
const createdRows = [];
async function fixture(name) {
  const id = randomUUID();
  const user = await admin('/users', 'POST', {userId: id, name: 'Synthetic: ' + name});
  users.push(id);
  assert.equal(user.$id, id);
  const token = await admin('/users/' + id + '/tokens', 'POST', {length: 32, expire: 300});
  const session = await admin('/account/sessions/token', 'POST', {userId: id, secret: token.secret});
  const secret = session.secret;
  if (!secret) throw new Error('Session secret unavailable from the test project.');
  return {id, session: secret};
}
async function read(id, identity) { return request(rows + '/' + id, {session: identity.session}); }
async function deny(operation) {
  await assert.rejects(operation, e => [401,403,404].includes(e.status));
}
let failed = false;
try {
  const table = await admin('/tablesdb/' + encodeURIComponent(database) + '/tables/devices');
  assert.equal(table.rowSecurity, true);
  assert.deepEqual(table.$permissions, []);
  const device = await fixture('device');
  const owner = await fixture('owner');
  const outsider = await fixture('outsider');
  const data = buildDeviceRegistration(device.id, 'Synthetic device');
  let row = await admin(rows, 'POST', data);
  createdRows.push(device.id);
  assert.equal((await read(device.id, device)).$id, device.id);
  await deny(() => read(device.id, owner));
  await deny(() => read(device.id, outsider));
  console.log('PASS: unlinked row is private');
  row = await admin(rows + '/' + device.id, 'PATCH', {
    permissions: ownerDevicePermissions(row, device.id, owner.id, 'link'),
  });
  assert.equal((await read(device.id, owner)).$id, device.id);
  await deny(() => read(device.id, outsider));
  await deny(() => request(rows + '/' + device.id, {
    method: 'PATCH', body: {data: {name: 'Unauthorized edit'}}, session: owner.session,
  }));
  console.log('PASS: owner read-only access; outsider denied');
  row = await admin(rows + '/' + device.id, 'PATCH', {
    permissions: ownerDevicePermissions(row, device.id, owner.id, 'unlink'),
  });
  await deny(() => read(device.id, owner));
  assert.equal((await read(device.id, device)).$id, device.id);
  console.log('PASS: unlink removes owner access and preserves device access');
} catch (e) {
  failed = true;
  console.error('Integration test failed:', e.message);
} finally {
  for (const id of createdRows.reverse()) {
    try { await admin(rows + '/' + id, 'DELETE'); }
    catch { failed = true; console.error('Manual cleanup required for synthetic row:', id); }
  }
  for (const id of users.reverse()) {
    try { await admin('/users/' + id, 'DELETE'); }
    catch { failed = true; console.error('Manual cleanup required for synthetic account:', id); }
  }
  if (failed) process.exitCode = 1;
}
