import { parseCdslTransactionsV2Text } from './cdslV2Transactions';

const DEMAT_SAMPLE = `
STATEMENT OF TRANSACTIONS FOR THE PERIOD FROM 01-04-2022 TO 30-04-2022
ISIN Security
ADANI POWER LIMITED -
INE814H01011
ON-CR TD:312005
TX:618831
1201090011331821 13-04-2022               0.000    15.000               --       15.000              0
JUBILANT FOODWORKS LIMITED#NEW EQUITY SHARES
INE797F01020
CA-Split 00469714 Cr Current Balance
21-04-2022                 0.000         5.000           --        5.000              0
JUBILANT FOODWORKS LIMITED - EQUITY SHARES
INE797F01012
CA-Split 00469714 Db Current Balance
21-04-2022            1.000            --    1.000            0.000              0
HOLDING STATEMENT AS ON 30-04-2022
`;

const MF_SAMPLE = `
MUTUAL FUND UNITS HELD WITH MF/RTA
STATEMENT OF TRANSACTIONS FOR THE PERIOD FROM 01-04-2026 TO 30-04-2026
ABAKKUS MUTUAL FUND
SCRG - ABAKKUS SMALL CAP FUND - Regular Growth
ISIN : INF2JJD01185 UCC :
Date Transaction Description Amount NAV Price Units Stamp Duty
13-04-2026 Lateral Shift In 68720 281895.91 10.513 10.513 26814.031 14.09 0 0
13-04-2026 S T P In 68723 54679.27 10.513 10.513 5201.11 2.73 0 0
LFRG - Abakkus Liquid Fund - Regular
ISIN : INF2JJD01078 UCC :
07-04-2026 Purchase(NAV Dt : 06/04/2026) 65629 936653.17 101.738 101.738 9206.522 46.83 0 0
12-04-2026 S T P Out 68722 -54682 101.8516 101.847 -537.08 0 0 0
HOLDING STATEMENT AS ON 30-04-2026
`;

const demat = parseCdslTransactionsV2Text(DEMAT_SAMPLE, 'fam_1');
if (demat.length !== 3) throw new Error(`Expected 3 demat transactions, got ${demat.length}`);
if (demat[0].action !== 'BUY' || demat[0].units !== '15') throw new Error('Demat credit parsing failed');
if (demat[1].action !== 'BUY' || demat[1].units !== '5') throw new Error('Corporate-action credit parsing failed');
if (demat[2].action !== 'SELL' || demat[2].units !== '-1') throw new Error('Corporate-action debit parsing failed');

const mf = parseCdslTransactionsV2Text(MF_SAMPLE, 'fam_1');
if (mf.length !== 4) throw new Error(`Expected 4 MF transactions, got ${mf.length}`);
const purchase = mf.find(t => t.action === 'BUY' && t.amount?.value === '936653.17');
if (!purchase || purchase.units !== '9206.522' || purchase.nav?.value !== '101.738') {
  throw new Error('MF purchase parsing failed');
}
const stpOut = mf.find(t => t.action === 'SELL' && t.amount?.value === '54682');
if (!stpOut || stpOut.units !== '-537.08') throw new Error('MF STP out parsing failed');

console.log(`CDSL V2 transaction tests passed: ${demat.length + mf.length} transactions`);
