import {
  parseStatementFile as parseLegacyStatementFile,
  extractPdfTextInBrowser,
  normalizeIndianDate,
  parseCdslCasText,
  parseCamsKfinCasText,
  getSampleCdslJune2026,
  getSampleCdslJuly2026,
  getSampleCdslSep2024,
} from './casParserLegacy';
import type { CASParseResult } from './casParserLegacy';
import { parseCdslCasV2Text } from './cdslV2Parser';
import { attachCdslTransactions } from './cdslV2Transactions';
import { canonicalCdslToLegacyResult } from './casV2Adapter';

export type { CASParseResult };
export { extractPdfTextInBrowser, normalizeIndianDate, parseCdslCasText, parseCamsKfinCasText };
export { getSampleCdslJune2026, getSampleCdslJuly2026, getSampleCdslSep2024 };

/**
 * V2 entry point.
 * CDSL statements are parsed by the source-specific V2 pipeline. NSDL and
 * CAMS/KFintech remain on the legacy path until their own V2 parsers pass the
 * required corpus validation.
 */
export async function parseStatementFile(
  file: File,
  password?: string,
  existingLedger: { holdingName: string; eventDate: string; totalAmountMinor: string }[] = [],
  targetOwnerId = 'fam_1'
): Promise<CASParseResult> {
  const isPdf = file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf';
  if (!isPdf) return parseLegacyStatementFile(file, password, existingLedger, targetOwnerId);

  const normalizedPassword = password?.trim().toUpperCase();
  const arrayBuffer = await file.arrayBuffer();
  const { fullText } = await extractPdfTextInBrowser(arrayBuffer, normalizedPassword);

  const isCdsl = /Central Depository Services\s*\(India\)\s*Limited/i.test(fullText);
  if (!isCdsl) {
    return parseLegacyStatementFile(file, normalizedPassword, existingLedger, targetOwnerId);
  }

  const canonical = attachCdslTransactions(
    parseCdslCasV2Text(fullText, targetOwnerId),
    fullText
  );
  return canonicalCdslToLegacyResult(canonical) as CASParseResult;
}
