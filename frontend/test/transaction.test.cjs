const test = require('node:test');
const assert = require('node:assert/strict');

test('amount input preserves exact cents and rejects invalid precision', async () => {
  const { parseCents, formatCents } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(parseCents('12.30'), 1230);
  assert.equal(parseCents('.05'), 5);
  assert.equal(parseCents('99999999.99'), 9999999999);
  for (const input of ['0', '12.301', '-2.00', '1e2', '', '100000000']) {
    assert.equal(parseCents(input), null, input);
  }
  assert.equal(formatCents(123045), '1,230.45');
});

test('local day is taken from the device calendar', async () => {
  const { localDay } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(localDay(new Date(2026, 10, 25)), '2026-11-25');
});

test('category names have a fallback for unknown codes', async () => {
  const { categoryLabel } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(categoryLabel('DINING', 'EXPENSE'), '餐饮');
  assert.equal(categoryLabel('UNRECOGNIZED', 'EXPENSE'), '其他支出');
});

test('month navigation crosses year boundaries', async () => {
  const { shiftLedgerMonth } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(shiftLedgerMonth('2026-01', -1), '2025-12');
  assert.equal(shiftLedgerMonth('2026-12', 1), '2027-01');
});

test('saved month is accepted only if supported and not in the future', async () => {
  const { resolveLedgerMonth } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(resolveLedgerMonth('2026-10', '2026-11'), '2026-10');
  for (const invalid of [null, '', '2026-1', '2026-00', '2026-12', '1899-12']) {
    assert.equal(resolveLedgerMonth(invalid, '2026-11'), '2026-11', String(invalid));
  }
});

test('income categories are separate and monthly balance uses exact cents', async () => {
  const { categoriesFor, categoryLabel, summarizeTransactions } =
    await import('../src/pages/ledger/transaction.ts');
  assert.equal(categoryLabel('SALARY', 'INCOME'), '工资');
  assert.equal(categoryLabel('DINING', 'INCOME'), '其他收入');
  assert.ok(categoriesFor('INCOME').every(([code]) => code !== 'DINING'));
  assert.deepEqual(
    summarizeTransactions([
      { direction: 'INCOME', amountCents: 500000 },
      { direction: 'INCOME', amountCents: 3 },
      { direction: 'EXPENSE', amountCents: 1230 },
    ]),
    { income: 500003, expense: 1230, balance: 498773 },
  );
});

test('account opening balances support signed exact cents and strict precision', async () => {
  const { parseOpeningBalance } = await import('../src/pages/ledger/transaction.ts');
  for (const [input, expected] of [
    ['', 0],
    ['-0.00', 0],
    ['+100.05', 10005],
    ['-100.05', -10005],
    ['-.50', -50],
    ['-99999999.99', -9999999999],
  ]) {
    assert.equal(parseOpeningBalance(input), expected, input);
  }
  for (const input of ['--1', '￥2', '-100000000', '0.001', '1e2', '+']) {
    assert.equal(parseOpeningBalance(input), null, input);
  }
});
