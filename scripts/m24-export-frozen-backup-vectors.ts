/**
 * M24 exporter-originated synthetic interoperability vectors.
 * Run against the PINNED frozen tree with: npx tsx scripts/m24-export-frozen-backup-vectors.ts
 * Imports frozen exporters rather than reimplementing their cipher wire format.
 * Capture stdout to an untracked fixture file and compare with an independent
 * Kotlin/JCE decoder before claiming cross-language compatibility.
 * No personal data, live vaults, or credentials are used.
 */
import { packBackup, unpackBackup } from '../src/lib/backup';
import { hashSecret, packHash } from '../src/lib/auth';
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
// Build the synthetic account with the *actual* frozen auth.ts hasher.
 // Account/recovery hashes use 210k rounds, NOT the 600k backup envelope KDF.
const loginHash = await hashSecret(password);
const q1Hash = await hashSecret('first city');
const q2Hash = await hashSecret('blue sea');
const snapshot = {
  kind: 'GUDVAULT',
  version: 2,
  exportedAt: '2026-10-16T00:00:00.000Z',
  account: {
    email: 'm24-owner@example.invalid',
    firstName: 'Fixture',
    lastName: 'Only',
    passwordSalt: loginHash.salt,
    passwordHash: loginHash.hash,
    passwordIterations: loginHash.iterations,
    passwordChangedAt: '2026-10-16T00:00:00.000Z',
    panMasked: 'ABCDE****F',
    panForStatements: 'ABCDE1234F',
    dateOfBirth: '1990-01-01',
    q1Id: 'city',
    q1Hash: packHash(q1Hash),
    q2Id: 'pet',
    q2Hash: packHash(q2Hash),
    createdAt: '2026-10-16T00:00:00.000Z',
  },
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
const modernOpened = await unpackBackup(modern, email, password);
const legacyOpened = await unpackBackup(
  new TextEncoder().encode(JSON.stringify(legacy)), email, password
);
if (JSON.stringify(modernOpened) !== json ||
    JSON.stringify(legacyOpened) !== json) {
  throw new Error('Frozen pack/unpack producer roundtrip lost unknown fields or scaled integers');
}
if (loginHash.iterations !== 210_000 ||
    q1Hash.iterations !== 210_000 || q2Hash.iterations !== 210_000) {
  throw new Error('Frozen account-hash PBKDF2 round count has drifted');
}
function digest(bytes: Uint8Array) {
  return createHash('sha256').update(bytes).digest('hex');
}
const output = {
  producer: 'frozen src/lib/backup.ts packBackup + src/lib/vault.ts encryptJson',
  classification: 'synthetic EXPORTER-EXECUTED data, NOT captured historical user archive',
  originalSnapshotSha256: digest(Buffer.from(json)),
  producerRoundtripVerified: true,
  frozenAccountHashRounds: loginHash.iterations,
  email: email.trim().toLowerCase(),
  testPassword: password,
  modernGUDVLT01Base64: Buffer.from(modern).toString('base64'),
  modernSha256: digest(modern),
  legacyKOSHA1Json: JSON.stringify(legacy),
  legacySha256: digest(Buffer.from(JSON.stringify(legacy))),
  expectedExactSnapshotJson: json,
};
process.stdout.write(JSON.stringify(output, null, 2) + '\n');
