/**
 * M24 exporter-originated synthetic interoperability vectors.
 * Run against the PINNED frozen tree with: npx tsx scripts/m24-export-frozen-backup-vectors.ts
 * Imports frozen exporters rather than reimplementing their cipher wire format.
 * Capture stdout to an untracked fixture file and compare with an independent
 * Kotlin/JCE decoder before claiming cross-language compatibility.
 * No personal data, live vaults, or credentials are used.
 */
import { packBackup } from '../src/lib/backup';
import { encryptJson } from '../src/lib/vault';
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

// Validate the EXACT frozen Git blobs, not merely the presence of similarly named exports.
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
for (const [relative, expected] of [
  ['src/lib/backup.ts', '5643af9eeb4ec00dfbf919b58cf998ffab67e858'],
  ['src/lib/vault.ts', 'fa311299c08f3a327a465e1175664d778229bb3b'],
] as const) {
  const source = readFileSync(resolve(root, relative));
  const gitBlob = createHash('sha1').update(`blob ${source.length}\0`).update(source).digest('hex');
  if (gitBlob !== expected) throw new Error(`Frozen source drift: ${relative}`);
}
const password = 'm24-fixture-password-not-a-secret';
const email = ' M24-Owner@example.invalid ';
const snapshot = {
  kind: 'GUDVAULT',
  version: 2,
  exportedAt: '2026-10-16T00:00:00.000Z',
  account: { email: 'm24-owner@example.invalid' },
  familyMembers: [{ id: 'owner-A', role: 'Self' }],
  holdings: [{ id: 'holding-A', ownerId: 'owner-A',
    unknownNested: { preserved: ['opaque', 1, true] } }],
  ledgerEvents: [{ ownerId: 'owner-A',
    quantityScaled: '1234567890123', totalAmountMinor: '9007199254740993' }],
  accounts: [{ id: 'account-A', ownerId: 'owner-A' }],
  goals: [],
  casLatest: null,
};
const json = JSON.stringify(snapshot);
const modern = await packBackup(snapshot as never, email, password);
const legacy = await encryptJson(snapshot, password);
function digest(bytes: Uint8Array) {
  return createHash('sha256').update(bytes).digest('hex');
}
const output = {
  producer: 'frozen src/lib/backup.ts packBackup + src/lib/vault.ts encryptJson',
  classification: 'synthetic EXPORTER-EXECUTED data, NOT captured historical user archive',
  originalSnapshotSha256: digest(Buffer.from(json)),
  email: email.trim().toLowerCase(),
  testPassword: password,
  modernGUDVLT01Base64: Buffer.from(modern).toString('base64'),
  modernSha256: digest(modern),
  legacyKOSHA1Json: JSON.stringify(legacy),
  legacySha256: digest(Buffer.from(JSON.stringify(legacy))),
  expectedExactSnapshotJson: json,
};
process.stdout.write(JSON.stringify(output, null, 2) + '\n');
