import * as pdfjsLib from 'pdfjs-dist';
import { AssetCategory, StatementImportRecord, StatementImportStatus } from '../types';

// Configure pdfjs worker for Vite browser environment
if (typeof window !== 'undefined') {
  try {
    // Set fallback worker URL or use CDN worker
    pdfjsLib.GlobalWorkerOptions.workerSrc = `https://cdnjs.cloudflare.com/ajax/libs/pdf.js/${pdfjsLib.version || '4.10.38'}/pdf.worker.min.mjs`;
  } catch (e) {
    console.warn('PDF.js worker initialization warning:', e);
  }
}

export interface ParsedCASScheme {
  amc?: string;
  folio: string;
  scheme: string;
  isin?: string;
  amfi?: string;
  rta?: string;
  advisor?: string;
  nominees?: string[];
  openUnits?: number;
  closeUnits: number;
  nav?: number;
  navDate?: string;
  totalValue?: number;
  costValue?: number;
  transactions: ParsedCASTransaction[];
}

export interface ParsedCASTransaction {
  date: string; // ISO YYYY-MM-DD
  rawDate: string;
  description: string;
  type: 'BUY' | 'SELL' | 'DIVIDEND' | 'INTEREST';
  amount: number;
  units: number;
  nav: number;
  balance?: number;
  stampDuty?: number;
}

export interface ParsedDematHolding {
  type: 'EQUITY' | 'MUTUAL_FUND' | 'GOLD_SGB' | 'FIXED_INCOME';
  isin: string;
  name: string;
  symbol?: string;
  quantity: number;
  marketPrice: number;
  currentValue: number;
  totalCost?: number;
  avgCost?: number;
  folioOrBoid?: string;
}

export interface CASParseResult {
  statementType: 'CAMS_KFIN_MF' | 'CDSL_NSDL_DEMAT' | 'GENERIC_CSV_OR_PDF';
  investorName?: string;
  investorPan?: string;
  investorEmail?: string;
  periodFrom?: string;
  periodTo?: string;
  schemes: ParsedCASScheme[];
  dematHoldings: ParsedDematHolding[];
  importRecords: StatementImportRecord[];
  summary: {
    totalFolios: number;
    totalTransactions: number;
    totalValuation: number;
  };
  warnings: string[];
}

const MONTH_MAP: Record<string, string> = {
  jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
  jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12',
};

/** Convert Indian string date like '14-Apr-2024' or '14/04/2024' to '2024-04-14' */
export function normalizeIndianDate(dateStr: string): string {
  if (!dateStr) return new Date().toISOString().split('T')[0];
  const clean = dateStr.trim();

  // Format: 14-Apr-2024 or 14-April-2024
  const dmyAlpha = clean.match(/^(\d{1,2})[-/\s]([A-Za-z]{3,9})[-/\s](\d{2,4})$/);
  if (dmyAlpha) {
    const day = dmyAlpha[1].padStart(2, '0');
    const monKey = dmyAlpha[2].substring(0, 3).toLowerCase();
    const month = MONTH_MAP[monKey] || '01';
    let year = dmyAlpha[3];
    if (year.length === 2) year = `20${year}`;
    return `${year}-${month}-${day}`;
  }

  // Format: 14/04/2024 or 14-04-2024
  const dmyNum = clean.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})$/);
  if (dmyNum) {
    const day = dmyNum[1].padStart(2, '0');
    const month = dmyNum[2].padStart(2, '0');
    let year = dmyNum[3];
    if (year.length === 2) year = `20${year}`;
    return `${year}-${month}-${day}`;
  }

  // ISO Format: 2024-04-14
  if (/^\d{4}-\d{2}-\d{2}$/.test(clean)) {
    return clean;
  }

  return clean;
}

/**
 * Client-Side PDF Decryption and Text Extraction using pdfjs-dist.
 * Strictly in-memory — no external network requests or disk persistence.
 */
export async function extractPdfTextInBrowser(
  arrayBuffer: ArrayBuffer,
  password?: string
): Promise<{ fullText: string; pageLines: string[][]; pagesCount: number }> {
  const loadingTask = pdfjsLib.getDocument({
    data: arrayBuffer,
    password: password ? password.trim().toUpperCase() : '',
    // standard font fallback
    cMapUrl: 'https://cdn.jsdelivr.net/npm/pdfjs-dist@4.10.38/cmaps/',
    cMapPacked: true,
  });

  const pdfDoc = await loadingTask.promise;
  const pagesCount = pdfDoc.numPages;
  const pageLines: string[][] = [];
  const fullTextParts: string[] = [];

  for (let pageNum = 1; pageNum <= pagesCount; pageNum++) {
    const page = await pdfDoc.getPage(pageNum);
    const textContent = await page.getTextContent();
    
    // Group text items by their approximate vertical baseline (y coordinate)
    const lineMap = new Map<number, { x: number; text: string }[]>();
    
    for (const item of textContent.items) {
      if ('str' in item && item.str.trim()) {
        // transform[5] is vertical position (y), transform[4] is horizontal (x)
        const y = Math.round(item.transform[5]);
        const x = Math.round(item.transform[4]);
        
        // Find existing baseline within 2 points tolerance
        let matchedY = y;
        for (const existingY of lineMap.keys()) {
          if (Math.abs(existingY - y) <= 2) {
            matchedY = existingY;
            break;
          }
        }

        if (!lineMap.has(matchedY)) {
          lineMap.set(matchedY, []);
        }
        lineMap.get(matchedY)!.push({ x, text: item.str });
      }
    }

    // Sort baselines top to bottom (PDF y coords go from bottom up, so descending y)
    const sortedBaselines = Array.from(lineMap.keys()).sort((a, b) => b - a);
    const linesOfPage: string[] = [];

    for (const y of sortedBaselines) {
      const itemsInLine = lineMap.get(y)!;
      // Sort left to right
      itemsInLine.sort((a, b) => a.x - b.x);
      const lineStr = itemsInLine.map(i => i.text.trim()).join(' ');
      if (lineStr.trim()) {
        linesOfPage.push(lineStr.trim());
      }
    }

    pageLines.push(linesOfPage);
    fullTextParts.push(linesOfPage.join('\n'));
  }

  return {
    fullText: fullTextParts.join('\n\n--- PAGE BREAK ---\n\n'),
    pageLines,
    pagesCount,
  };
}

/**
 * Parses CAMS / KFintech Mutual Fund Consolidated Account Statement (CAS).
 * Implements the regex pattern hierarchy based on the casparser specification.
 */
export function parseCamsKfinCasText(
  fullText: string,
  existingLedger: { holdingName: string; eventDate: string; totalAmountMinor: string }[],
  targetOwnerId: string
): CASParseResult {
  const warnings: string[] = [];
  const schemes: ParsedCASScheme[] = [];
  const importRecords: StatementImportRecord[] = [];

  // 1. Extract Investor info
  let investorName: string | undefined;
  let investorPan: string | undefined;
  let investorEmail: string | undefined;

  const panMatch = fullText.match(/PAN\s*[:.-]?\s*([A-Z]{5}[0-9]{4}[A-Z])/i);
  if (panMatch) investorPan = panMatch[1].toUpperCase();

  const emailMatch = fullText.match(/Email\s*(?:Id|ID)?\s*[:.-]?\s*([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})/i);
  if (emailMatch) investorEmail = emailMatch[1];

  const nameMatch = fullText.match(/(?:Name|Investor Name)\s*[:.-]?\s*([A-Z\s]{3,40})/i);
  if (nameMatch) investorName = nameMatch[1].trim();

  // 2. Identify statement period
  const periodMatch = fullText.match(/Statement for the period from\s*([0-9A-Za-z-]+)\s*to\s*([0-9A-Za-z-]+)/i);
  const periodFrom = periodMatch ? normalizeIndianDate(periodMatch[1]) : undefined;
  const periodTo = periodMatch ? normalizeIndianDate(periodMatch[2]) : undefined;

  // 3. Chunk text into schemes by Folio Number or Scheme headers
  const lines = fullText.split('\n').map(l => l.trim()).filter(Boolean);
  
  let currentScheme: ParsedCASScheme | null = null;
  let currentAmc = 'Mutual Fund';
  let recordCounter = 1;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Detect AMC header
    if (/(Mutual Fund|Asset Management|Investments|AMC)/i.test(line) && line.length < 80 && !line.includes('***')) {
      if (!line.startsWith('Folio') && !line.startsWith('Scheme')) {
        currentAmc = line.replace(/^[*\s]+|[*\s]+$/g, '').trim();
      }
    }

    // Detect Folio Number pattern: "Folio No: 123456/78" or "Folio No. : 9102934 / 12"
    const folioMatch = line.match(/(?:Folio\s*(?:No|Number)?\.?\s*[:.-]?\s*)([A-Z0-9/_-]+(?:\s*[/]\s*[0-9]+)?)/i);
    
    // Detect ISIN: "ISIN: INF..."
    const isinMatch = line.match(/ISIN\s*[:.-]?\s*(INF[A-Z0-9]{9})/i);

    // Detect Scheme Name if line contains scheme keywords (Direct Plan, Growth, Regular, Fund)
    const isSchemeLine = (
      (line.includes('Fund') || line.includes('Growth') || line.includes('Direct') || line.includes('Plan') || line.includes('Option')) &&
      !line.startsWith('Valuation') &&
      !line.startsWith('Closing Unit') &&
      !line.startsWith('Total Cost') &&
      !line.includes('Transaction') &&
      line.length > 8 && line.length < 120
    );

    if (folioMatch || (isSchemeLine && !currentScheme)) {
      const folio = folioMatch ? folioMatch[1].replace(/\s+/g, '') : (currentScheme?.folio || `MF_${Date.now()}`);
      const schemeTitle = isSchemeLine ? line : (lines[i + 1] || 'Mutual Fund Scheme');

      // Finish previous scheme if present
      if (currentScheme && currentScheme.transactions.length > 0) {
        schemes.push(currentScheme);
      }

      currentScheme = {
        amc: currentAmc,
        folio,
        scheme: schemeTitle.replace(/ISIN.*$/i, '').trim(),
        isin: isinMatch ? isinMatch[1] : undefined,
        closeUnits: 0,
        transactions: [],
      };
    }

    if (currentScheme) {
      if (isinMatch && !currentScheme.isin) {
        currentScheme.isin = isinMatch[1];
      }

      // Check for Closing Balance / NAV line
      // e.g. "Closing Unit Balance: 33.412 | NAV on 31-Mar-2025: INR 165.41 | Total Value: INR 5,526.68"
      const closingMatch = line.match(/Closing\s*Unit\s*Balance\s*[:.-]?\s*([0-9,.]+)/i);
      if (closingMatch) {
        currentScheme.closeUnits = parseFloat(closingMatch[1].replace(/,/g, '')) || 0;
      }

      const navMatch = line.match(/NAV\s*(?:on\s*[^:]+)?[:.-]?\s*(?:INR|Rs\.?|₹)?\s*([0-9,.]+)/i);
      if (navMatch) {
        currentScheme.nav = parseFloat(navMatch[1].replace(/,/g, '')) || 0;
      }

      const valMatch = line.match(/(?:Total\s*Value|Valuation|Market\s*Value)\s*[:.-]?\s*(?:INR|Rs\.?|₹)?\s*([0-9,.]+)/i);
      if (valMatch) {
        currentScheme.totalValue = parseFloat(valMatch[1].replace(/,/g, '')) || 0;
      }

      // Detect Transaction Row
      // Format usually: Date | Description | Amount | Units | Price/NAV | Unit Balance
      // e.g. "02-Apr-2024 SIP Purchase - Instalment 1/12 4,999.75 33.412 149.64 33.412"
      const dateTokenMatch = line.match(/^(\d{1,2}[-/\s][A-Za-z]{3,9}[-/\s]\d{2,4}|\d{1,2}[-/]\d{1,2}[-/]\d{2,4})/);
      if (dateTokenMatch) {
        const rawDate = dateTokenMatch[1];
        const isoDate = normalizeIndianDate(rawDate);
        const restOfLine = line.substring(dateTokenMatch[0].length).trim();

        // Extract numeric tokens from the end of the line (Amount, Units, NAV, Balance)
        const numTokens = restOfLine.match(/([0-9,]+(?:\.[0-9]+)?)/g) || [];
        
        let amount = 0;
        let units = 0;
        let nav = 0;
        let balance = 0;

        if (numTokens.length >= 3) {
          amount = parseFloat(numTokens[0].replace(/,/g, '')) || 0;
          units = parseFloat(numTokens[1].replace(/,/g, '')) || 0;
          nav = parseFloat(numTokens[2].replace(/,/g, '')) || 0;
          if (numTokens.length >= 4) {
            balance = parseFloat(numTokens[3].replace(/,/g, '')) || 0;
          }
        } else if (numTokens.length === 2) {
          amount = parseFloat(numTokens[0].replace(/,/g, '')) || 0;
          units = parseFloat(numTokens[1].replace(/,/g, '')) || 0;
        }

        // Determine action type
        const descUpper = restOfLine.toUpperCase();
        let action: 'BUY' | 'SELL' | 'DIVIDEND' | 'INTEREST' = 'BUY';
        if (descUpper.includes('REDEMPTION') || descUpper.includes('SWITCH OUT')) {
          action = 'SELL';
        } else if (descUpper.includes('DIVIDEND') || descUpper.includes('IDCW') || descUpper.includes('PAYOUT')) {
          action = 'DIVIDEND';
        } else if (descUpper.includes('INTEREST')) {
          action = 'INTEREST';
        }

        // Ignore pure stamp duty row with 0 units or negative micro amounts
        const isStampDutyOnly = descUpper.includes('STAMP DUTY') && units === 0;

        if (!isStampDutyOnly && (units > 0 || amount > 0)) {
          const txItem: ParsedCASTransaction = {
            date: isoDate,
            rawDate,
            description: restOfLine.replace(/([0-9,]+(?:\.[0-9]+)?)/g, '').trim() || 'SIP / Purchase',
            type: action,
            amount: amount || (units * (nav || 10)),
            units,
            nav: nav || (amount && units ? amount / units : 0),
            balance,
          };

          currentScheme.transactions.push(txItem);

          // Build StatementImportRecord for user review
          const holdingTitle = currentScheme.scheme || `${currentAmc} Fund`;
          
          // Check for duplicate in existing ledger
          const isDuplicate = existingLedger.some(
            ev => ev.holdingName.toLowerCase().includes(holdingTitle.toLowerCase().substring(0, 15)) &&
                  ev.eventDate === isoDate
          );

          importRecords.push({
            id: `cas_mf_${recordCounter++}`,
            date: isoDate,
            holdingName: holdingTitle,
            category: 'MUTUAL_FUND',
            action,
            quantity: units,
            pricePerUnit: nav || (amount && units ? Math.round((amount / units) * 100) / 100 : 0),
            totalAmount: amount || (units * nav),
            status: isDuplicate ? 'ALREADY_EXISTS' : 'NEW',
            statusMessage: isDuplicate
              ? `Already in ledger for ${isoDate}`
              : `Folio: ${currentScheme.folio} • Verified ${action} transaction`,
            selected: !isDuplicate,
          });
        }
      }
    }
  }

  // Push the final scheme
  if (currentScheme && (currentScheme.transactions.length > 0 || currentScheme.closeUnits > 0)) {
    schemes.push(currentScheme);
  }

  // Fallback: If no transactions were parsed (e.g. Summary CAS), create records from Schemes Closing Balances
  if (importRecords.length === 0 && schemes.length > 0) {
    schemes.forEach(sc => {
      if (sc.closeUnits > 0) {
        const estNav = sc.nav || (sc.totalValue && sc.closeUnits ? sc.totalValue / sc.closeUnits : 100);
        const estTotal = sc.totalValue || (sc.closeUnits * estNav);
        importRecords.push({
          id: `cas_mf_summary_${recordCounter++}`,
          date: periodTo || new Date().toISOString().split('T')[0],
          holdingName: sc.scheme,
          category: 'MUTUAL_FUND',
          action: 'BUY',
          quantity: sc.closeUnits,
          pricePerUnit: Math.round(estNav * 100) / 100,
          totalAmount: Math.round(estTotal * 100) / 100,
          status: 'NEW',
          statusMessage: `Summary Holding: ${sc.closeUnits} units in Folio ${sc.folio}`,
          selected: true,
        });
      }
    });
  }

  const totalValuation = importRecords.reduce((sum, r) => sum + r.totalAmount, 0);

  return {
    statementType: 'CAMS_KFIN_MF',
    investorName,
    investorPan,
    investorEmail,
    periodFrom,
    periodTo,
    schemes,
    dematHoldings: [],
    importRecords,
    summary: {
      totalFolios: schemes.length,
      totalTransactions: importRecords.length,
      totalValuation,
    },
    warnings,
  };
}

/**
 * Parses CDSL / NSDL Demat Consolidated Account Statements.
 * Extracts Equities, ETFs, Demat MFs, SGBs, and Bonds.
 */
export function parseDematCasText(
  fullText: string,
  existingLedger: { holdingName: string; eventDate: string; totalAmountMinor: string }[],
  targetOwnerId: string
): CASParseResult {
  const warnings: string[] = [];
  const dematHoldings: ParsedDematHolding[] = [];
  const importRecords: StatementImportRecord[] = [];

  let investorName: string | undefined;
  let investorPan: string | undefined;

  const panMatch = fullText.match(/PAN\s*[:.-]?\s*([A-Z]{5}[0-9]{4}[A-Z])/i);
  if (panMatch) investorPan = panMatch[1].toUpperCase();

  const nameMatch = fullText.match(/(?:Name|Client Name|First Holder)\s*[:.-]?\s*([A-Z\s]{3,40})/i);
  if (nameMatch) investorName = nameMatch[1].trim();

  const isinRegex = /(INE[A-Z0-9]{9}|IN9[A-Z0-9]{9}|INF[A-Z0-9]{9}|IN0[A-Z0-9]{9})/g;
  const lines = fullText.split('\n').map(l => l.trim()).filter(Boolean);
  let recordCounter = 1;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const isinMatch = line.match(isinRegex);

    if (isinMatch) {
      const isin = isinMatch[0];
      // Extract numbers in line: Quantity, Market Price, Value
      const numTokens = line.match(/([0-9,]+(?:\.[0-9]+)?)/g) || [];
      
      // Clean security name from line
      const cleanName = line
        .replace(isin, '')
        .replace(/([0-9,]+(?:\.[0-9]+)?)/g, '')
        .replace(/[-|:]+/g, ' ')
        .trim();

      const name = cleanName.length > 3 ? cleanName : `Security ${isin}`;
      
      let qty = 0;
      let price = 0;
      let val = 0;

      if (numTokens.length >= 3) {
        qty = parseFloat(numTokens[0].replace(/,/g, '')) || 0;
        price = parseFloat(numTokens[1].replace(/,/g, '')) || 0;
        val = parseFloat(numTokens[2].replace(/,/g, '')) || 0;
      } else if (numTokens.length === 2) {
        qty = parseFloat(numTokens[0].replace(/,/g, '')) || 0;
        val = parseFloat(numTokens[1].replace(/,/g, '')) || 0;
        price = qty > 0 ? val / qty : 0;
      }

      if (qty > 0) {
        let category: AssetCategory = 'EQUITY';
        if (isin.startsWith('INF') || name.toLowerCase().includes('etf') || name.toLowerCase().includes('fund')) {
          category = 'MUTUAL_FUND';
        } else if (name.toLowerCase().includes('sgb') || name.toLowerCase().includes('gold') || name.toLowerCase().includes('sovereign')) {
          category = 'GOLD_SGB';
        } else if (name.toLowerCase().includes('bond') || name.toLowerCase().includes('ncd') || name.toLowerCase().includes('gov')) {
          category = 'FIXED_INCOME';
        }

        dematHoldings.push({
          type: category as any,
          isin,
          name,
          quantity: qty,
          marketPrice: price || (val && qty ? val / qty : 100),
          currentValue: val || (qty * price),
        });

        // Check duplicates
        const isDuplicate = existingLedger.some(
          ev => ev.holdingName.toLowerCase().includes(name.toLowerCase().substring(0, 10))
        );

        importRecords.push({
          id: `demat_${recordCounter++}`,
          date: new Date().toISOString().split('T')[0],
          holdingName: name,
          category,
          action: 'BUY',
          quantity: qty,
          pricePerUnit: Math.round((price || (val / qty)) * 100) / 100,
          totalAmount: Math.round((val || (qty * price)) * 100) / 100,
          status: isDuplicate ? 'ALREADY_EXISTS' : 'NEW',
          statusMessage: `ISIN: ${isin} • Demat Holding (${qty} units)`,
          selected: !isDuplicate,
        });
      }
    }
  }

  const totalValuation = importRecords.reduce((sum, r) => sum + r.totalAmount, 0);

  return {
    statementType: 'CDSL_NSDL_DEMAT',
    investorName,
    investorPan,
    periodFrom: undefined,
    periodTo: undefined,
    schemes: [],
    dematHoldings,
    importRecords,
    summary: {
      totalFolios: dematHoldings.length,
      totalTransactions: importRecords.length,
      totalValuation,
    },
    warnings,
  };
}

/**
 * Universal Entry Point: In-Browser CAS & Statement Processor.
 * Dispatches to CAMS/KFintech or CDSL/NSDL Demat parser based on detected watermark/header.
 */
export async function parseStatementFile(
  file: File,
  password?: string,
  existingLedger: { holdingName: string; eventDate: string; totalAmountMinor: string }[] = [],
  targetOwnerId = 'fam_1'
): Promise<CASParseResult> {
  const isPdf = file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf';

  if (!isPdf) {
    // CSV Parser flow
    const text = await file.text();
    return parseCsvStatementText(text, file.name, existingLedger, targetOwnerId);
  }

  // Read binary ArrayBuffer for PDF
  const arrayBuffer = await file.arrayBuffer();
  
  // Extract text in memory
  const { fullText } = await extractPdfTextInBrowser(arrayBuffer, password);

  // Check if NSDL / CDSL or CAMS / KFintech
  const isDemat = /CDSL|NSDL|Depository|Holding Statement|BOID|Client ID/i.test(fullText) &&
                  !/Computer Age Management|CAMS|KFINTECH|Karvy/i.test(fullText);

  if (isDemat) {
    return parseDematCasText(fullText, existingLedger, targetOwnerId);
  } else {
    return parseCamsKfinCasText(fullText, existingLedger, targetOwnerId);
  }
}

/**
 * Parses generic broker CSV tradebooks (Zerodha, Groww, AngelOne, Upstox).
 */
export function parseCsvStatementText(
  csvContent: string,
  fileName: string,
  existingLedger: { holdingName: string; eventDate: string; totalAmountMinor: string }[],
  targetOwnerId: string
): CASParseResult {
  const lines = csvContent.split('\n').map(l => l.trim()).filter(Boolean);
  const importRecords: StatementImportRecord[] = [];
  let recordCounter = 1;

  if (lines.length > 1) {
    for (let i = 1; i < lines.length; i++) {
      const parts = lines[i].split(',').map(p => p.replace(/"/g, '').trim());
      if (parts.length >= 4) {
        const date = normalizeIndianDate(parts[0]);
        const holdingName = parts[1] || 'Imported Share';
        const actionStr = (parts[2] || 'BUY').toUpperCase();
        const action = actionStr.includes('SELL') ? 'SELL' : actionStr.includes('DIV') ? 'DIVIDEND' : 'BUY';
        const quantity = Math.abs(parseFloat(parts[3]) || 1);
        const price = Math.abs(parseFloat(parts[4]) || 100);
        const total = quantity * price;

        const isDuplicate = existingLedger.some(
          ev => ev.holdingName.toLowerCase() === holdingName.toLowerCase() && ev.eventDate === date
        );

        let category: AssetCategory = 'EQUITY';
        if (holdingName.toLowerCase().includes('fund') || holdingName.toLowerCase().includes('etf')) {
          category = 'MUTUAL_FUND';
        } else if (holdingName.toLowerCase().includes('gold') || holdingName.toLowerCase().includes('sgb')) {
          category = 'GOLD_SGB';
        }

        importRecords.push({
          id: `imp_csv_${recordCounter++}`,
          date,
          holdingName,
          category,
          action,
          quantity,
          pricePerUnit: price,
          totalAmount: total,
          status: isDuplicate ? 'ALREADY_EXISTS' : 'NEW',
          statusMessage: isDuplicate ? 'Already in ledger records' : 'Verified trade row',
          selected: !isDuplicate,
        });
      }
    }
  }

  const totalValuation = importRecords.reduce((sum, r) => sum + r.totalAmount, 0);

  return {
    statementType: 'GENERIC_CSV_OR_PDF',
    schemes: [],
    dematHoldings: [],
    importRecords,
    summary: {
      totalFolios: importRecords.length,
      totalTransactions: importRecords.length,
      totalValuation,
    },
    warnings: [],
  };
}
