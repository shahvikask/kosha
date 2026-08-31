import assert from 'node:assert/strict';
import { money, normalizeDecimal, normalizeISIN, normalizePan, CanonicalCASDocument } from './casV2Model';
import { validateCanonicalCAS } from './casV2Validation';

assert.equal(normalizeDecimal('1,50,46,788.1400'), '15046788.14');
assert.equal(normalizeDecimal('00012.500'), '12.5');
assert.equal(normalizeDecimal('bad'), undefined);
assert.equal(normalizeISIN('INF-123456789'), 'INF123456789');
assert.equal(normalizePan('abcde1234f'), 'ABCDE1234F');
assert.deepEqual(money('1,250.00'), { value: '1250', currency: 'INR' });

const valid: CanonicalCASDocument = {
  schemaVersion: 1,
  source: 'CDSL',
  ownerId: 'owner-test',
  investorPan: 'ABCDE1234F',
  periodTo: '2026-07-31',
  holdings: [{
    kind: 'HOLDING',
    source: 'CDSL',
    ownerId: 'owner-test',
    asOfDate: '2026-07-31',
    security: { isin: 'INE123456789', name: 'Example Ltd' },
    quantity: '10',
    marketPrice: { value: '100', currency: 'INR' },
    marketValue: { value: '1000', currency: 'INR' },
  }],
  transactions: [],
  summary: {
    source: 'CDSL', ownerId: 'owner-test',
    dematValue: { value: '1000', currency: 'INR' },
    mutualFundValue: { value: '0', currency: 'INR' },
    grandTotal: { value: '1000', currency: 'INR' },
  },
  warnings: [],
};

const validResult = validateCanonicalCAS(valid);
assert.equal(validResult.valid, true);
assert.equal(validResult.findings.length, 0);

const invalid: CanonicalCASDocument = {
  ...valid,
  ownerId: '',
  holdings: [{ ...valid.holdings[0], quantity: 'not-a-number' }],
};
const invalidResult = validateCanonicalCAS(invalid);
assert.equal(invalidResult.valid, false);
assert.ok(invalidResult.findings.some(f => f.code === 'DOCUMENT_OWNER_MISSING'));
assert.ok(invalidResult.findings.some(f => f.code === 'HOLDING_QUANTITY_INVALID'));

const mismatch: CanonicalCASDocument = {
  ...valid,
  holdings: [{ ...valid.holdings[0], marketValue: { value: '900', currency: 'INR' } }],
};
const mismatchResult = validateCanonicalCAS(mismatch);
assert.equal(mismatchResult.valid, true);
assert.ok(mismatchResult.findings.some(f => f.code === 'HOLDING_VALUE_MISMATCH'));

console.log('CAS V2 model/validation tests passed');
