import test from 'node:test';
import assert from 'node:assert/strict';
import { ownerDevicePermissions } from './owner-device-access.mjs';

const device = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const owner = 'google-owner';
const other = 'other-owner';
const read = id => 'read("user:' + id + '")';
const row = permissions => ({ $id: device, deviceId: device, $permissions: permissions });

test('link grants read-only metadata access to exactly two accounts', () => {
  const result = ownerDevicePermissions(row([read(device)]), device, owner, 'link');
  assert.deepEqual(result, [read(device), read(owner)]);
  assert.ok(!result.includes(read(other)));
});
test('repeat link is idempotent; unlink preserves device access', () => {
  assert.deepEqual(ownerDevicePermissions(row([read(device), read(owner)]), device, owner, 'link'), [read(device), read(owner)]);
  assert.deepEqual(ownerDevicePermissions(row([read(device), read(owner)]), device, owner, 'unlink'), [read(device)]);
});
test('reject broad, unexpected or writable ACLs', () => {
  for (const p of ['read("any")', read(other), 'update("user:' + owner + '")']) {
    assert.throws(() => ownerDevicePermissions(row([read(device), p]), device, owner, 'link'));
  }
});
test('reject invalid identifiers or wrong rows', () => {
  assert.throws(() => ownerDevicePermissions(row([read(device)]), device, 'x")', 'link'));
  assert.throws(() => ownerDevicePermissions(row([read(device)]), device, device, 'link'));
  assert.throws(() => ownerDevicePermissions({...row([read(device)]), deviceId: other}, device, owner, 'link'));
});
