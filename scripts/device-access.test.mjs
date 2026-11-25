import test from 'node:test';
import assert from 'node:assert/strict';
import { buildDeviceRegistration } from './device-access.mjs';

const a = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
const b = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

test('device metadata row is readable only by the provisioned account', () => {
  const row = buildDeviceRegistration(a, 'Test A', new Date('2026-01-01T00:00:00Z'));
  assert.equal(row.rowId, a);
  assert.equal(row.data.deviceId, a);
  assert.equal(row.data.lastSeen, '2026-01-01T00:00:00.000Z');
  assert.deepEqual(row.permissions, ['read("user:' + a + '")']);
  assert.ok(!row.permissions.includes('read("user:' + b + '")'));
  assert.ok(!row.permissions.some(p => /any|users|write|update|delete|create/.test(p)));
});

test('different accounts receive separate row IDs and ACLs', () => {
  const first = buildDeviceRegistration(a, 'Test A');
  const second = buildDeviceRegistration(b, 'Test B');
  assert.notEqual(first.rowId, second.rowId);
  assert.notDeepEqual(first.permissions, second.permissions);
});

test('reject malformed account identifiers and empty labels', () => {
  assert.throws(() => buildDeviceRegistration('any', 'Test'));
  assert.throws(() => buildDeviceRegistration(a, '  '));
  assert.throws(() => buildDeviceRegistration(a, 'x'.repeat(111)));
});
