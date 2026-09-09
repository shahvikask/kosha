import { FamilyMember } from '../types';

export function normalizePan(pan?: string | null): string {
  return (pan || '').replace(/[^A-Z0-9*]/gi, '').toUpperCase();
}

/** True for a full PAN (ABCDE1234F) or a masked PAN (ABCDE****F). */
export function isPanShape(pan?: string | null): boolean {
  const p = normalizePan(pan);
  return /^[A-Z]{5}[0-9]{4}[A-Z]$/.test(p) || /^[A-Z]{5}\*{4}[A-Z]$/.test(p);
}

/** Compare-key: ABCDE1234F and ABCDE****F both become ABCDE****F. */
export function panFingerprint(pan?: string | null): string | null {
  const p = normalizePan(pan);
  if (!isPanShape(p)) return null;
  return `${p.slice(0, 5)}****${p.slice(9)}`;
}

export function pansMatch(a?: string | null, b?: string | null): boolean {
  const fa = panFingerprint(a);
  const fb = panFingerprint(b);
  return !!fa && !!fb && fa === fb;
}

export function memberMatchesPan(member: FamilyMember, printed?: string | null): boolean {
  if (!printed) return false;
  return pansMatch(member.panForStatements, printed) || pansMatch(member.panMasked, printed);
}

export function memberHasPan(member: FamilyMember): boolean {
  return !!panFingerprint(member.panForStatements) || !!panFingerprint(member.panMasked);
}

export function displayPan(pan?: string | null): string {
  return panFingerprint(pan) || normalizePan(pan) || 'not printed';
}

export type PanImportCheck =
  | { kind: 'NO_PDF_PAN' }
  | { kind: 'MATCH' }
  | { kind: 'SWITCH'; matched: FamilyMember }
  | { kind: 'UNKNOWN_PAN'; printed: string }
  | { kind: 'MEMBER_HAS_NO_PAN'; printed: string };

export function checkImportPan(
  selected: FamilyMember | undefined,
  members: FamilyMember[],
  printedPan?: string | null
): PanImportCheck {
  const printed = normalizePan(printedPan);
  if (!isPanShape(printed)) return { kind: 'NO_PDF_PAN' };
  if (selected && memberMatchesPan(selected, printed)) return { kind: 'MATCH' };
  const other = members.find(m => m.id !== selected?.id && memberMatchesPan(m, printed));
  if (other) return { kind: 'SWITCH', matched: other };
  if (selected && !memberHasPan(selected)) return { kind: 'MEMBER_HAS_NO_PAN', printed };
  return { kind: 'UNKNOWN_PAN', printed };
}

export function findDuplicatePanMember(
  members: FamilyMember[],
  pan: string,
  excludeId?: string
): FamilyMember | undefined {
  if (!isPanShape(pan)) return undefined;
  return members.find(m => m.id !== excludeId && memberMatchesPan(m, pan));
}

export function panBlocksCommit(check: PanImportCheck): boolean {
  return check.kind === 'SWITCH' || check.kind === 'UNKNOWN_PAN' || check.kind === 'MEMBER_HAS_NO_PAN';
}
