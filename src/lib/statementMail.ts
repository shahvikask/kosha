/**
 * Which statement mail is safe to download.
 * CDSL and NPS put the person in the attachment name.
 * NSDL, CAMS, and KFintech do not. Those are downloaded only
 * when a member has Gmail sync on, then matched by PAN after parsing.
 */

export type StatementIssuer = 'CDSL' | 'NSDL' | 'CAMS' | 'KFINTECH' | 'NPS';

export interface StatementName {
  issuer: 'CDSL' | 'NPS';
  casId?: string;
  pran?: string;
  variant?: 'TXN' | 'HLD';
}

export interface MailMemberRef {
  id: string;
  gmailSync?: boolean;
  casId?: string;
  pran?: string;
  casIdSource?: 'profile' | 'import';
  pranSource?: 'profile' | 'import';
  casSyncAfter?: string;
  pranSyncAfter?: string;
}

export type MailDecision =
  | { action: 'download'; ownerId?: string }
  | { action: 'skip'; remember: boolean; reason: string };

const CDSL_NAME = /^([A-Za-z]{3}\d{4})_([A-Za-z0-9]{6,20})_(TXN|HLD)\.pdf$/i;
const NPS_NAME = /^(\d{10,12})_([A-Za-z]{3}\d{4})\.pdf$/i;

export function statementBasename(name: string): string {
  const slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
  return (slash >= 0 ? name.slice(slash + 1) : name).trim();
}

/** CDSL: AUG2026_AA07970379_TXN.pdf. NPS: 1120330238_Aug2026.pdf (PRAN is usually 12 digits; some mail uses 10). */
export function classifyStatementFilename(name: string): StatementName | null {
  const base = statementBasename(name);
  const cdsl = CDSL_NAME.exec(base);
  if (cdsl) {
    return {
      issuer: 'CDSL',
      casId: cdsl[2].toUpperCase(),
      variant: cdsl[3].toUpperCase() as 'TXN' | 'HLD',
    };
  }
  const nps = NPS_NAME.exec(base);
  if (nps) return { issuer: 'NPS', pran: nps[1] };
  return null;
}

export function issuerFromSender(from: string): StatementIssuer | null {
  const f = (from || '').toLowerCase();
  if (f.includes('cdslstatement.com')) return 'CDSL';
  if (f.includes('nsdl-cas@') || f.includes('nsdlcas.nsdl.com') || f.includes('@nsdl.co.in')) return 'NSDL';
  if (f.includes('camsonline.com')) return 'CAMS';
  if (f.includes('kfintech.com')) return 'KFINTECH';
  if (f.includes('proteantech.in')) return 'NPS';
  return null;
}

export function decideStatementMail(
  file: { filename: string; from: string },
  members: MailMemberRef[],
): MailDecision {
  const enabled = members.filter(m => m.gmailSync);
  if (!enabled.length) return { action: 'skip', remember: false, reason: 'no member has Gmail sync on' };

  const named = classifyStatementFilename(file.filename);
  const sender = issuerFromSender(file.from);

  if (named?.issuer === 'CDSL' || sender === 'CDSL') {
    if (!named?.casId) return { action: 'skip', remember: false, reason: 'CDSL file name has no CAS ID' };
    const owner = enabled.find(m => (m.casId || '').toUpperCase() === named.casId);
    if (owner) return { action: 'download', ownerId: owner.id };
    const anyRecorded = enabled.some(m => (m.casId || '').trim());
    if (!anyRecorded) return { action: 'skip', remember: false, reason: 'import one CDSL statement by hand first' };
    return { action: 'skip', remember: true, reason: 'CAS ID does not match a member' };
  }

  if (named?.issuer === 'NPS' || sender === 'NPS') {
    if (!named?.pran) return { action: 'skip', remember: false, reason: 'NPS file name has no PRAN' };
    const owner = enabled.find(m => (m.pran || '') === named.pran);
    if (owner) return { action: 'download', ownerId: owner.id };
    const anyRecorded = enabled.some(m => (m.pran || '').trim());
    if (!anyRecorded) return { action: 'skip', remember: false, reason: 'import one NPS statement by hand first' };
    return { action: 'skip', remember: true, reason: 'PRAN does not match a member' };
  }

  if (sender === 'NSDL' || sender === 'CAMS' || sender === 'KFINTECH') {
    return { action: 'download' };
  }

  return { action: 'skip', remember: true, reason: 'not a statement sender' };
}

const MONTH_NUM: Record<string, string> = {
  jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
  jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12',
};

/** YYYY-MM from a CDSL or NPS attachment name. Null for other issuers. */
export function statementMonthKey(name: string): string | null {
  const base = statementBasename(name);
  const cdsl = /^([A-Za-z]{3})(\d{4})_/i.exec(base);
  if (cdsl) {
    const mm = MONTH_NUM[cdsl[1].toLowerCase()];
    if (mm) return `${cdsl[2]}-${mm}`;
  }
  const nps = /^\d{10,12}_([A-Za-z]{3})(\d{4})\.pdf$/i.exec(base);
  if (nps) {
    const mm = MONTH_NUM[nps[1].toLowerCase()];
    if (mm) return `${nps[2]}-${mm}`;
  }
  return null;
}

function statementRank(name: string): string {
  const month = statementMonthKey(name) || '0000-00';
  const txn = /_TXN\.pdf$/i.test(statementBasename(name)) ? '1' : '0';
  return `${month}${txn}`;
}

/**
 * Gmail lists newest mail first, and a later import replaces the books.
 * Keep one CDSL file per CAS ID and one NPS file per PRAN: the latest month,
 * and the transaction file when that month also has a holdings file.
 */
export function newestNamedStatements<T extends { filename: string }>(
  files: T[],
): { keep: T[]; older: T[] } {
  const best = new Map<string, T>();
  const older: T[] = [];
  const unnamed: T[] = [];
  for (const file of files) {
    const named = classifyStatementFilename(file.filename);
    const id = named?.casId ? `c:${named.casId}` : named?.pran ? `p:${named.pran}` : '';
    if (!id) {
      unnamed.push(file);
      continue;
    }
    const prev = best.get(id);
    if (!prev || statementRank(file.filename) > statementRank(prev.filename)) {
      if (prev) older.push(prev);
      best.set(id, file);
    } else {
      older.push(file);
    }
  }
  return { keep: [...best.values(), ...unnamed], older };
}

export function memberHasStatementId(member: MailMemberRef): boolean {
  return Boolean((member.casId || '').trim() || (member.pran || '').trim());
}

/** YYYY-MM from an ISO period, or from a CDSL/NPS file name. */
export function monthKeyOf(periodOrName?: string | null): string {
  const raw = (periodOrName || '').trim();
  if (/^\d{4}-\d{2}/.test(raw)) return raw.slice(0, 7);
  return statementMonthKey(raw) || '';
}

/**
 * Profile-typed id: keep every month, so the book and the history can be built.
 * Id filled by a hand import: keep only months after that statement.
 * Same month keeps the transaction file over the holdings file.
 */
export function filesForMember<T extends { filename: string }>(
  files: T[],
  member: MailMemberRef,
): { keep: T[]; older: T[] } {
  const keep: T[] = [];
  const older: T[] = [];
  const best = new Map<string, T>();
  for (const file of files) {
    const named = classifyStatementFilename(file.filename);
    const month = statementMonthKey(file.filename) || '';
    if (named?.casId) {
      if ((member.casId || '').toUpperCase() !== named.casId) {
        older.push(file);
        continue;
      }
      const cutoff = member.casIdSource === 'import' ? monthKeyOf(member.casSyncAfter) : '';
      if (cutoff && month && month <= cutoff) {
        older.push(file);
        continue;
      }
      const key = `c:${named.casId}:${month || file.filename}`;
      const prev = best.get(key);
      if (!prev || statementRank(file.filename) > statementRank(prev.filename)) {
        if (prev) older.push(prev);
        best.set(key, file);
      } else older.push(file);
      continue;
    }
    if (named?.pran) {
      if ((member.pran || '') !== named.pran) {
        older.push(file);
        continue;
      }
      const cutoff = member.pranSource === 'import' ? monthKeyOf(member.pranSyncAfter) : '';
      if (cutoff && month && month <= cutoff) {
        older.push(file);
        continue;
      }
      const key = `p:${named.pran}:${month || file.filename}`;
      const prev = best.get(key);
      if (!prev || statementRank(file.filename) > statementRank(prev.filename)) {
        if (prev) older.push(prev);
        best.set(key, file);
      } else older.push(file);
      continue;
    }
    keep.push(file);
  }
  keep.push(...best.values());
  return { keep, older };
}
