import { randomUUID } from 'node:crypto';
import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';

// Run only on a trusted administrator workstation, never on Android.
const endpoint = 'https://sgp.cloud.appwrite.io/v1';
const projectId = '6b0600f60013dd532f05';
const key = process.env.APPWRITE_ENROLLMENT_KEY;
if (!key) throw new Error('Set APPWRITE_ENROLLMENT_KEY in the administrator environment.');
if (!stdin.isTTY || !stdout.isTTY) throw new Error('Use an interactive private terminal.');

async function request(path, body) {
  const response = await fetch(endpoint + path, {
    method: 'POST',
    redirect: 'error',
    headers: {
      'Content-Type': 'application/json',
      'X-Appwrite-Project': projectId,
      'X-Appwrite-Key': key,
    },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
  if (!response.ok) throw new Error('Appwrite request failed: HTTP ' + response.status);
  return response.json();
}

const rl = createInterface({ input: stdin, output: stdout });
let userId;
try {
  const label = (await rl.question('Approved device label: ')).trim();
  if (!label || label.length > 110) throw new Error('Use a label of 1–110 characters.');
  const approved = await rl.question(
    'Has the device owner explicitly approved this account enrollment? Type APPROVED: '
  );
  if (approved !== 'APPROVED') throw new Error('Enrollment cancelled.');
  // Fresh, isolated account; never reuse a human or existing device account.
  userId = randomUUID();
  await request('/users', { userId, name: 'Device: ' + label });
  const token = await request('/users/' + encodeURIComponent(userId) + '/tokens', {
    length: 32,
    expire: 300,
  });
  if (!token.secret || token.userId !== userId) throw new Error('Unexpected token response.');
  console.log('Transfer these credentials privately to the approved device owner:');
  console.log('Enrollment user ID:', userId);
  console.log('One-time token:', token.secret);
  console.log('Expires:', token.expire);
  console.log('No SMS permissions granted. Clear the terminal after enrollment.');
} catch (error) {
  console.error(error.message);
  if (userId) console.error('Check this account in the console before retrying:', userId);
  process.exitCode = 1;
} finally {
  rl.close();
}
