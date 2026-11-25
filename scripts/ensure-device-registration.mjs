import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import { buildDeviceRegistration } from './device-access.mjs';

// Administrative recovery of device metadata only. Never uploads messages.
const endpoint = 'https://sgp.cloud.appwrite.io/v1';
const project = '6b0600f60013dd532f05';
const database = '6b06f1f7003b65907da2';
const key = process.env.APPWRITE_ENROLLMENT_KEY;
if (!key || !stdin.isTTY || !stdout.isTTY) {
  throw new Error('Use an interactive administrator terminal with APPWRITE_ENROLLMENT_KEY.');
}
async function call(path, method = 'GET', body) {
  const response = await fetch(endpoint + path, {
    method, redirect: 'error', signal: AbortSignal.timeout(15000),
    headers: { 'X-Appwrite-Project': project, 'X-Appwrite-Key': key, 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  if (!response.ok) {
    const error = new Error('Appwrite HTTP ' + response.status);
    error.status = response.status;
    throw error;
  }
  return response.json();
}
const rl = createInterface({ input: stdin, output: stdout });
try {
  const userId = (await rl.question('Existing dedicated device account ID: ')).trim();
  const label = (await rl.question('Approved device label: ')).trim();
  const registration = buildDeviceRegistration(userId, label);
  const user = await call('/users/' + encodeURIComponent(userId));
  if (user.$id !== userId || !user.status || !user.name?.startsWith('Device: ')) {
    throw new Error('Expected an active dedicated device account.');
  }
  const approved = await rl.question('Owner approved recovery of this private metadata record? Type APPROVED: ');
  if (approved !== 'APPROVED') throw new Error('Cancelled.');
  const path = '/tablesdb/' + database + '/tables/devices/rows';
  const rowPath = path + '/' + encodeURIComponent(userId);
  let row;
  try {
    row = await call(rowPath);
  } catch (error) {
    if (error.status !== 404) throw error;
    try {
      row = await call(path, 'POST', registration);
    } catch (createError) {
      if (createError.status !== 409) throw createError;
      row = await call(rowPath);
    }
  }
  if (row.$id !== userId || row.deviceId !== userId ||
      JSON.stringify(row.$permissions) !== JSON.stringify(registration.permissions)) {
    throw new Error('Unexpected record or permissions. Review manually; no changes made to existing record.');
  }
  console.log('Private device metadata verified. No message access granted.');
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
} finally {
  rl.close();
}
