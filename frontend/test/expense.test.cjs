const test = require('node:test');
const assert = require('node:assert/strict');

test('amount input preserves exact cents and rejects invalid precision', async () => {
  const { parseCents, formatCents } = await import('../src/pages/ledger/expense.ts');
  assert.equal(parseCents('12.30'), 1230);
  assert.equal(parseCents('.05'), 5);
  assert.equal(parseCents('99999999.99'), 9999999999);
  for (const input of ['0', '12.301', '-2.00', '1e2', '', '100000000']) {
    assert.equal(parseCents(input), null, input);
  }
  assert.equal(formatCents(123045), '1,230.45');
});

test('local day is taken from the device calendar', async () => {
  const { localDay } = await import('../src/pages/ledger/expense.ts');
  assert.equal(localDay(new Date(2026, 10, 25)), '2026-11-25');
});

test('category names have a fallback for unknown codes', async () => {
  const { categoryLabel } = await import('../src/pages/ledger/expense.ts');
  assert.equal(categoryLabel('DINING'), '餐饮');
  assert.equal(categoryLabel('UNRECOGNIZED'), '其他支出');
});
