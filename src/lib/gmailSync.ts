import { nativeCommand } from './license';
import { parseCasPdfWithPython, statementPasswordsToTry, CasImportMeta, CasLatestState, CasFileType } from './casPython';
import { FamilyMember, StatementImportRecord } from '../types';
import { decideStatementMail, classifyStatementFilename, issuerFromSender, filesForMember, memberHasStatementId, statementMonthKey } from './statementMail';
import { pansMatch } from './pan';

export interface GmailAccountStatus {
  email: string;
  after: string;
  first: boolean;
  page?: string;
  historyId?: string;
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
const MAX_DOWNLOADS = 8;

function yieldToUi(): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, 0));
}


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
  window.dispatchEvent(new CustomEvent('gud-gmail-sync', { detail: true }));
  try {
  const accounts = await gmailStatus();
  if (!accounts.length) return 'Connect a Gmail account first.';
  const enabled = opts.members.filter(m => m.gmailSync);
  if (!enabled.length) return 'Turn on Gmail sync for a family member first.';

  let imported = 0;
  let skipped = 0;
  let failed = 0;
  let more = false;
  let waitingForId = false;
  let npsWaiting = false;
  const importedAsOf = new Map<string, string>();
  const replaced: string[] = [];

  const knownAsOf = (ownerId: string, fileType: string): string => {
    const member = opts.members.find(m => m.id === ownerId);
    const fromProfile = fileType === 'NPS' ? member?.pranSyncAfter : member?.casSyncAfter;
    return importedAsOf.get(`${ownerId}|${fileType}`)
      || opts.casLatest?.byOwner[ownerId]?.[fileType as CasFileType]?.periodTo
      || fromProfile
      || '';
  };

  for (const account of accounts) {
    let pageToken = account.page || '';
    const startPage = pageToken;
    let pages = 0;
    let finished = false;
    let held = false;
    let accountFailed = 0;
    let historyId = account.historyId || '';
    const batch: ListedFile[] = [];

    while (pages < MAX_PAGES) {
      const listed = await nativeCommand<{ files?: ListedFile[]; pageToken?: string; historyId?: string }>('gmail_search', {
        email: account.email,
        pageToken,
      });
      if (listed.historyId) historyId = listed.historyId;
      for (const file of listed.files || []) {
        const month = statementMonthKey(file.filename) || '';
        const namedSeen = classifyStatementFilename(file.filename);
        const owner = opts.members.find(m => namedSeen?.casId ? (m.casId || '').toUpperCase() === namedSeen.casId : namedSeen?.pran && (m.pran || '').includes(namedSeen.pran));
        const bookMonth = owner ? knownAsOf(owner.id, namedSeen?.issuer || '').slice(0, 7) : '';
        if (await opts.seen(file.messageId, file.attachmentId) && (!month || !bookMonth || month <= bookMonth)) {
          skipped += 1;
          continue;
        }
        const decision = decideStatementMail(file, opts.members);
        if (decision.action === 'skip') {
          const named = classifyStatementFilename(file.filename);
          if (named?.pran && !opts.members.some(m => (m.pran || '').length > 0)) {
            npsWaiting = true;
            held = true;
          } else if (decision.remember) {
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

    const ready = enabled.filter(memberHasStatementId);
    if (!ready.length) waitingForId = true;
    const older: ListedFile[] = [];
    const keep: ListedFile[] = [];
    const claimed = new Set<string>();
    for (const member of ready) {
      const picked = filesForMember(batch, member);
      for (const file of picked.keep) {
        const key = `${file.messageId}:${file.attachmentId}`;
        if (claimed.has(key)) continue;
        claimed.add(key);
        keep.push(file);
      }
      older.push(...picked.older.filter(file => !claimed.has(`${file.messageId}:${file.attachmentId}`)));
    }
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
      if (decision.action === 'download' && decision.ownerId && fileMonth) {
        const fileType = named?.issuer || issuerFromSender(file.from) || '';
        const have = knownAsOf(decision.ownerId, fileType);
        if (have && fileMonth < have.slice(0, 7)) {
          await opts.markSeen(file.messageId, file.attachmentId, file.filename, decision.ownerId);
          skipped += 1;
          continue;
        }
      }
      if (decision.action !== 'download') continue;
      const likelyType = named?.issuer || issuerFromSender(file.from) || '';
      const likelyOwner = decision.ownerId;
      used += 1;
      await yieldToUi();
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
          const incoming = (parsed.periodTo || fileMonth || '').slice(0, 7);
          const have = previous.slice(0, 7);
          if (incoming && have && incoming < have) {
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
            closeHoldingIds: opts.casLatest?.byOwner[member.id]?.[fileType]?.holdingIds,
          };
          await opts.importStatementRecords(parsed.importRecords, member.id, 'REPLACE_SNAPSHOT', meta);
          const month = (parsed.periodTo || fileMonth || '').slice(0, 7);
          const count = parsed.holdings?.length || parsed.importRecords.length;
          replaced.push(`Replaced ${fileType} book with ${month || 'the newest statement'}, ${count} holdings.`);
          const patch: Partial<FamilyMember> = {};
          if (named?.casId && !member.casId) {
            patch.casId = named.casId;
            patch.casIdSource = 'import';
          }
          if (named?.pran && !member.pran) {
            patch.pran = named.pran;
            patch.pranSource = 'import';
          }
          if (fileType === 'NPS' && parsed.periodTo) patch.pranSyncAfter = parsed.periodTo;
          if (fileType !== 'NPS' && parsed.periodTo) patch.casSyncAfter = parsed.periodTo;
          if (patch.casId || patch.pran || patch.casSyncAfter || patch.pranSyncAfter) await opts.updateFamilyMember(member.id, patch);
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
    if (finished && !held && !deferred && accountFailed === 0 && historyId) {
      await nativeCommand('gmail_advance', { email: account.email, done: true, historyId });
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

  const tail = [
    npsWaiting ? 'NPS is waiting for a PRAN on the profile.' : '',
    waitingForId && !opts.members.some(m => m.casId || m.pran) ? 'Enter a CAS ID or PRAN, or import one statement by hand, then sync again.' : '',
    more ? 'More mail is still there. Tap sync again.' : '',
    failed ? `Could not open ${failed}.` : '',
  ].filter(Boolean).join(' ');
  const summary = replaced.length ? `${replaced.join(' ')} ${tail}`.trim() : `Imported ${imported}. Skipped ${skipped}. ${tail}`.trim();
  return summary;
  } finally {
    window.dispatchEvent(new CustomEvent('gud-gmail-sync', { detail: false }));
  }
}