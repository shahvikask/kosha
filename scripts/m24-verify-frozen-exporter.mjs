/**
 * Developer-only M24 cross-language regression. The full GitHub checkout with
 * installed node_modules, a local Kotlin compiler and Java is required.
 * Optional argument: path to a synthetic exporter-generated fixture JSON.
 * No live holdings, PAN or credentials may ever be supplied to this tool.
 */
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
function sha256(bytes) { return createHash('sha256').update(bytes).digest('hex'); }
function blobSha(bytes) {
  return createHash('sha1').update(`blob ${bytes.length}\0`).update(bytes).digest('hex');
}
for (const [rel,sha] of [
  ['src/lib/backup.ts','5643af9eeb4ec00dfbf919b58cf998ffab67e858'],
  ['src/lib/vault.ts','fa311299c08f3a327a465e1175664d778229bb3b'],
  ['src/lib/auth.ts','088177a21e44b7e938eed2cb6f0818ca8c9fcdeb'],
]) {
  if (blobSha(readFileSync(join(root,rel))) !== sha) throw Error('Frozen source drift: '+rel);
}
const fileArg=process.argv[2];
const raw = fileArg ? readFileSync(resolve(fileArg),'utf8') :
  execFileSync(join(root,'node_modules','.bin','tsx'),
    [join(root,'scripts','m24-export-frozen-backup-vectors.ts')],
    {cwd:root,encoding:'utf8',maxBuffer:10_000_000});
const fixture=JSON.parse(raw);
if (!fixture.producerRoundtripVerified || fixture.frozenAccountHashRounds!==210000 ||
    fixture.classification!=='synthetic EXPORTER-EXECUTED data, NOT captured historical user archive')
  throw Error('Missing authentic synthetic frozen producer evidence');
const binary=Buffer.from(fixture.modernGUDVLT01Base64,'base64');
const legacy=JSON.parse(fixture.legacyKOSHA1Json);
const plain=fixture.expectedExactSnapshotJson;
if (sha256(binary)!==fixture.modernSha256 ||
    sha256(Buffer.from(fixture.legacyKOSHA1Json))!==fixture.legacySha256 ||
    sha256(Buffer.from(plain))!==fixture.originalSnapshotSha256)
  throw Error('Exporter fixture hashes do not match');
const account=JSON.parse(plain).account;
const fields=[legacy.magic,legacy.kdf,String(legacy.iterations),legacy.saltB64,
  legacy.ivB64,legacy.cipherB64,account.email,account.passwordSalt,
  account.passwordHash,String(account.passwordIterations),account.panForStatements,
  account.panMasked,account.dateOfBirth,account.q1Id,account.q1Hash,
  account.q2Id,account.q2Hash];
if(fields.length!==17 || fields.some(x=>typeof x!=='string'||x.includes('\n')))
  throw Error('Missing required synthetic exporter field');
const temp=mkdtempSync(join(tmpdir(),'gud-m24-interop-'));
try {
  writeFileSync(join(temp,'modern.kosha'),binary);
  writeFileSync(join(temp,'snapshot.json'),plain);
  writeFileSync(join(temp,'snapshot.sha256'),fixture.originalSnapshotSha256);
  writeFileSync(join(temp,'fields.txt'),fields.join('\n')+'\n');
  const javaSource=join(root,'android-native','core','database','src','main','kotlin','in','growurdhan','database');
  execFileSync('kotlinc',[
      join(javaSource,'LegacyBackupEnvelopeReader.kt'),
      join(javaSource,'RestoreIdentityGate.kt'),
      join(root,'scripts','m24-frozen-exporter-interop.kt'),
      '-include-runtime','-d',join(temp,'interop.jar')
    ],{cwd:root,stdio:'inherit'});
  execFileSync('java',['-jar',join(temp,'interop.jar'),temp],
    {cwd:root,stdio:'inherit'});
} finally { rmSync(temp,{recursive:true,force:true}); }
