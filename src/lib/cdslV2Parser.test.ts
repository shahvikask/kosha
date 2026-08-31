import assert from 'node:assert/strict';
import { parseCdslCasV2Text } from './cdslV2Parser';

// These fixtures are reduced, anonymized structural extracts from the supplied
// CDSL corpus. They contain no investor identity, PAN, address or account id.

const standard = `
Statement for the period from 01-Mar-2022 to 31-Mar-2022
CDSL Demat Accounts 11,58,620.20
Mutual Fund Folios 3,64,756.16
Total Portfolio Value 15,23,376.36
HOLDING STATEMENT AS ON 31-03-2022
AVENUE SUPERMARTS LIMITED
INE192R01011 39.000 -- -- -- 39.000 3999.450 1,55,978.55
MUTUAL FUND UNITS HELD AS ON 31-03-2022
Sample Small Cap Fund INF760K01JF9 17745005675/0 28243.224 37.86 7,09,180.73 10,69,288.46 3,60,107.73 50.78
`;

const pledged = `
Statement for the period from 01-Feb-2022 to 28-Feb-2022
CDSL Demat Accounts 11,58,620.20
Mutual Fund Folios 0.00
Total Portfolio Value 11,58,620.20
HOLDING STATEMENT AS ON 28-02-2022
Example Finance Limited
INE467B01029 33.000 -- 30.000 -- 3.000 3554.550 1,17,300.15
`;

const arnAndDecimal = `
Statement for the period from 01-Mar-2025 to 31-Mar-2025
CDSL Demat Accounts 5.00
Mutual Fund Folios 1,70,54,510.78
Total Portfolio Value 1,70,54,515.78
HOLDING STATEMENT AS ON 31-03-2025
NIPPON LIFE INDIA AM LTD
INF732E01037 0.005 -- -- -- 0.005 1000.000 5.00
MUTUAL FUND UNITS HELD AS ON 31-03-2025
Small Cap Fund INF760K01JF9 17745005675/0 ARN-190724 4568.857 34.4 1,31,376.15 1,57,168.68 1.69 0 812.64 25,792.53 19.63
Ultra Short Term Fund INF760K01DC9 1/0 ARN-190724 .08 3531.7107 253.77 282.54 .96 0 42.68
`;

const a = parseCdslCasV2Text(standard, 'owner-test');
assert.equal(a.periodTo, '2022-03-31');
assert.equal(a.summary?.grandTotal?.value, '1523376.36');
assert.equal(a.holdings.length, 2);
assert.equal(a.holdings[0].quantity, '39.000');
assert.equal(a.holdings[0].marketValue?.value, '155978.55');
assert.equal(a.holdings[1].quantity, '28243.224');
assert.equal(a.holdings[1].marketValue?.value, '1069288.46');

const b = parseCdslCasV2Text(pledged, 'owner-test');
assert.equal(b.holdings.length, 1);
assert.equal(b.holdings[0].quantity, '33.000');
assert.equal(b.holdings[0].marketPrice?.value, '3554.550');
assert.equal(b.holdings[0].marketValue?.value, '117300.15');

const c = parseCdslCasV2Text(arnAndDecimal, 'owner-test');
assert.equal(c.holdings.length, 3);
assert.equal(c.holdings[1].quantity, '4568.857');
assert.equal(c.holdings[1].marketPrice?.value, '34.4');
assert.equal(c.holdings[1].marketValue?.value, '157168.68');
assert.equal(c.holdings[2].quantity, '0.08');
assert.equal(c.holdings[2].marketPrice?.value, '3531.7107');
assert.equal(c.holdings[2].marketValue?.value, '282.54');

console.log('CDSL V2 parser structural tests passed');
