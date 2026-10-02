import { nativeCommand } from './license';
import { parseCasPdfWithPython, statementPasswordsToTry, CasImportMeta, CasLatestState, CasFileType } from './casPython';
import { FamilyMember, StatementImportRecord } from '../types';
import { decideStatementMail, classifyStatementFilename, issuerFromSender, newestNamedStatements, statementMonthKey } from './statementMail';
import { pansMatch } from './pan';

export interface GmailAccountStatus {
  email: string;
  after: string;
  first: boolean;
  page?: string;
}

interface ListedFile {
  messageId: string;
  attachmentId: string;
  filename: string;
  from: string;
  date: string;
}

type ImportFn = (
  records: StatementImportRecord[],
  targetOwnerId?: string,
  mode?: 'SYNC_UPDATE' | 'REPLACE_SNAPSHOT',
  casMeta?: CasImportMeta,
) => Promise<unknown>;

const MAX_PAGES = 4;
const MAX_DOWNLOADS = 12;

function fileFromBase64(name: string, b64: string): File {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new File([bytes], name, { type: 'application/pdf' });
}

async function parseWithPasswords(file: File, member: FamilyMember) {
  const passwords = statementPasswordsToTry({
    pan: member.panForStatements,
    name: member.name,
    dateOfBirth: member.dateOfBirth,
  });
  let last = 'Could not open the PDF.';
  for (const password of passwords) {
    try {
      return await parseCasPdfWithPython(file, password);
    } catch (e) {
      last = e instanceof Error ? e.message : last;
    }
  }
  throw new Error(last);
}

function monthOf(period?: string | null): string {
  return (period || '').slice(0, 7);
}

export async function gmailStatus(): Promise<GmailAccountStatus[]> {
  const row = await nativeCommand<{ accounts?: GmailAccountStatus[] }>('gmail_status');
  return row.accounts || [];
}

export async function connectGmail(): Promise<string> {
  const row = await nativeCommand<{ email?: string }>('gmail_connect');
  return row.email || '';
}

export async function disconnectGmail(email: string): Promise<void> {
  await nativeCommand('gmail_disconnect', { email });
}

export async function runGmailSync(opts: {
  members: FamilyMember[];
  casLatest?: CasLatestState;
  importStatementRecords: ImportFn;
  updateFamilyMember: (id: string, updates: Partial<FamilyMember>) => Promise<void>;
  seen: (messageId: string, attachmentId: string) => Promise<boolean>;
  markSeen: (messageId: string, attachmentId: string, filename: string, entityId: string) => Promise<void>;
}): Promise<string> {
  const accounts = await gmailStatus();
  if (!accounts.length) return 'Connect a Gmail account first.';
  const enabled = opts.members.filter(m => m.gmailSync);
  if (!enabled.length) return 'Turn on Gmail sync for a family member first.';

  let imported = 0;
  let skipped = 0;
  let failed = 0;
  let more = false;
  let waitingForId = false;
  const importedAsOf = new Map<string, string>();

  const knownAsOf = (ownerId: string, fileType: string): string => {
    return importedAsOf.get(`${ownerId}|${fileType}`)
      || opts.casLatest?.byOwner[ownerId]?.[fileType as CasFileType]?.periodTo
      || '';
  };

  for (const account of accounts) {
    let pageToken = account.page || '';
    const startPage = pageToken;
    let pages = 0;
    let finished = false;
    let held = false;
    let accountFailed = 0;
    const batch: ListedFile[] = [];

    while (pages < MAX_PAGES) {
      const listed = await nativeCommand<{ files?: ListedFile[]; pageToken?: string }>('gmail_search', {
        email: account.email,
        pageToken,
      });
      for (const file of listed.files || []) {
        if (await opts.seen(file.messageId, file.attachmentId)) {
          skipped += 1;
          continue;
        }
        const decision = decideStatementMail(file, opts.members);
        if (decision.action === 'skip') {
          if (decision.remember) {
            await opts.markSeen(file.messageId, file.attachmentId, file.filename, '');
          } else {
            held = true;
          }
          skipped += 1;
          continue;
        }
        batch.push(file);
      }
      pages += 1;
      pageToken = listed.pageToken || '';
      if (!pageToken) {
        finished = true;
        break;
      }
    }

    const { keep, older } = newestNamedStatements(batch);
    for (const file of older) {
      await opts.markSeen(file.messageId, file.attachmentId, file.filename, '');
      skipped += 1;
    }

    const downloads = [...keep].sort((a, b) => (statementMonthKey(b.filename) || '').localeCompare(statementMonthKey(a.filename) || ''));
    let deferred = false;
    let used = 0;
    for (const file of downloads) {
      if (used >= MAX_DOWNLOADS) {
        deferred = true;
        break;
      }
      const fileMonth = statementMonthKey(file.filename);
      const named = classifyStatementFilename(file.filename);
      const decision = decideStatementMail(file, opts.members);
      if (decision.action !== 'download') continue;
      const likelyType = named?.issuer || issuerFromSender(file.from) || '';
      const likelyOwner = decision.ownerId;
      if (fileMonth && likelyOwner && likelyType && monthOf(knownAsOf(likelyOwner, likelyType)) > fileMonth) {
        await opts.markSeen(file.messageId, file.attachmentId, file.filename, likelyOwner);
        skipped += 1;
        continue;
      }
      used += 1;
      try {
        const fetched = await nativeCommand<{ bytesB64?: string; error?: string }>('gmail_fetch', {
          email: account.email,
          messageId: file.messageId,
          attachmentId: file.attachmentId,
        });
        if (!fetched.bytesB64) throw new Error(fetched.error || 'Empty attachment.');
        const pdf = fileFromBase64(file.filename, fetched.bytesB64);
        const owners = decision.ownerId
          ? enabled.filter(m => m.id === decision.ownerId)
          : enabled;
        let done = false;
        let opened = false;
        for (const member of owners) {
          let parsed;
          try {
            parsed = await parseWithPasswords(pdf, member);
            opened = true;
          } catch {
            continue;
          }
          const panOk = !parsed.investorPan || pansMatch(parsed.investorPan, member.panForStatements || '');
          if (!decision.ownerId && parsed.investorPan && !panOk) continue;
          if (!decision.ownerId && !parsed.investorPan) continue;
          const fileType = (parsed.fileType || likelyType || 'CDSL') as CasFileType;
          const previous = knownAsOf(member.id, fileType);
          if (parsed.periodTo && previous && parsed.periodTo < previous) {
            await opts.markSeen(file.messageId, file.attachmentId, file.filename, member.id);
            skipped += 1;
            done = true;
            break;
          }
          const meta: CasImportMeta = {
            fileType,
            filename: file.filename,
            periodFrom: parsed.periodFrom,
            periodTo: parsed.periodTo,
            investorName: parsed.investorName,
            investorPan: parsed.investorPan,
            pdfGrandTotal: parsed.pdfTotals?.grand ?? parsed.summary.totalValuation ?? 0,
            parsedGrandTotal: parsed.parsedTotals?.holdingsValue ?? parsed.summary.totalValuation ?? 0,
            complete: parsed.parseComplete === true,
          };
          await opts.importStatementRecords(parsed.importRecords, member.id, 'REPLACE_SNAPSHOT', meta);
          const patch: Partial<FamilyMember> = {};
          if (named?.casId) patch.casId = named.casId;
          if (named?.pran) patch.pran = named.pran;
          if (patch.casId || patch.pran) await opts.updateFamilyMember(member.id, patch);
          if (parsed.periodTo) importedAsOf.set(`${member.id}|${fileType}`, parsed.periodTo);
          await opts.markSeen(file.messageId, file.attachmentId, file.filename, member.id);
          imported += 1;
          done = true;
          break;
        }
        if (!done) {
          if (opened && !decision.ownerId) {
            await opts.markSeen(file.messageId, file.attachmentId, file.filename, '');
            skipped += 1;
          } else {
            accountFailed += 1;
            failed += 1;
          }
        }
      } catch {
        accountFailed += 1;
        failed += 1;
      }
    }

    if (held) waitingForId = true;
    if (finished && !held && !deferred && accountFailed === 0) {
      await nativeCommand('gmail_advance', { email: account.email, done: true });
    } else if (held || deferred) {
      await nativeCommand('gmail_advance', { email: account.email, done: false, pageToken: startPage });
      more = true;
    } else if (!finished) {
      await nativeCommand('gmail_advance', { email: account.email, done: false, pageToken });
      more = true;
    } else {
      await nativeCommand('gmail_advance', { email: account.email, done: false, pageToken: '' });
      more = true;
    }
  }

  const tail = waitingForId
    ? ' Import one statement by hand first so the CAS ID or PRAN is recorded, then sync again.'
    : more
      ? ' More mail is still there. Tap sync again.'
      : '';
  return `Imported ${imported}. Skipped ${skipped}. Could not open ${failed}.${tail}`;
}