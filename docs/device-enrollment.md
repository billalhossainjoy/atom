# Device enrollment (administrator)

Cloud SMS synchronization remains disabled. This procedure creates a dedicated Appwrite account, a private device metadata row, and a short-lived sign-in token. It does not grant access to any SMS table, upload content, or prove possession of the Android Keystore key.

## Prerequisites

- Node.js 20 or newer on a trusted administrator workstation.
- In the Appwrite project console, create a separate enrollment API key with only `users.write` and `rows.write` scopes. Do not reuse the database key. Do not put the key in the Android application, Git, shell history, or logs.
- Verify the device owner's identity and obtain their explicit approval in person or over a trusted channel. The local app displays a public-key fingerprint; account enrollment is not yet cryptographically bound to it.

## Provision an approved device

In a private terminal on Fedora (Bash):

```bash
read -rsp 'Enrollment API key: ' APPWRITE_ENROLLMENT_KEY; echo
export APPWRITE_ENROLLMENT_KEY
node scripts/enroll-device.mjs
unset APPWRITE_ENROLLMENT_KEY
```

Enter a device label and confirm owner approval. The script creates a fresh account and a `devices` row whose ID and `deviceId` match the dedicated account ID. Only that account can read the row; no client can write it. The initial `lastSeen` is the provisioning timestamp, not a verified connection. It then displays an enrollment user ID and a token expiring in five minutes. Transfer these credentials privately to the approved owner. On Android, open Settings, prepare the device identity, and enter the credentials under Cloud enrollment. Clear the private terminal afterwards. Never post the token in a GitHub issue or chat.

## Revocation

Disconnect the session in Android settings or revoke the account's sessions in Appwrite Console. Disable the dedicated account if the device is lost or approval is withdrawn. Keep the tables private; do not grant access to all authenticated users.

## Local authorization tests

```bash
node --test scripts/device-access.test.mjs
```

These tests validate the generated ACL, not live Appwrite enforcement. The existing tables must keep row security enabled and table-level permissions empty. No SMS or conversation permissions are granted. Verify cross-account denial using synthetic test records before any production use.

## Link an approved device to the web dashboard

The owner first signs in at https://datom-dashboard.vercel.app using their own Google account. Verify their identity and obtain their explicit approval to link the device. Find the corresponding verified Appwrite user ID in the project Auth console. Do not treat an Android ID or device label as proof of ownership.

Use a separate administrator-only key with `users.read`, `rows.read` and `rows.write` for this operation. Run in a private terminal:

```bash
read -rsp 'Administrator API key: ' APPWRITE_ENROLLMENT_KEY; echo
export APPWRITE_ENROLLMENT_KEY
node scripts/link-device-owner.mjs
unset APPWRITE_ENROLLMENT_KEY
```

Select `link`, provide the dedicated device account ID and the verified owner account ID, then confirm approval. Refresh the dashboard to see the private device metadata. Select `unlink` to remove the owner's metadata access without removing the device account's access.

This grants only read permission on one existing device metadata row. No SMS or conversation permissions are changed. The script rejects unexpected ACLs and assumes a single owner per device. Run administrator changes serially. The registration recovery utility intentionally rejects existing rows with additional ACLs; do not overwrite those permissions.

Test the permission rules:

```bash
node --test scripts/*.test.mjs
```

## Remaining work before real data

- Implement server-side verification of the prepared public key and device ownership.
- Add explicit, separately revocable consent for the backup of selected content.
- Enforce owner-scoped authorization server-side, including negative tests for cross-account access. Do not rely on the submitted device ID for authorization.
- Verify the Android session flow and test a synthetic-data-only sync queue before enabling backup.
- Define retention and deletion controls.

Reference: https://appwrite.io/docs/products/auth/custom-token
