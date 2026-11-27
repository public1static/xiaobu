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

test('income categories are separate from expense categories', async () => {
  const { categoriesFor, categoryLabel } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(categoryLabel('SALARY', 'INCOME'), '工资');
  assert.equal(categoryLabel('DINING', 'INCOME'), '其他收入');
  assert.ok(categoriesFor('INCOME').every(([code]) => code !== 'DINING'));
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

test('a negative expense from the statement is rendered in exact cents', async () => {
  const { formatSignedCurrency } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(formatSignedCurrency(-3000), '−¥30.00');
});

test('load more depends only on the server page metadata', async () => {
  const { hasNextStatementPage } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(hasNextStatementPage(null), false);
  assert.equal(hasNextStatementPage({ page: 0, pageSize: 20, totalCount: 0 }), false);
  assert.equal(hasNextStatementPage({ page: 0, pageSize: 20, totalCount: 49 }), true);
  assert.equal(hasNextStatementPage({ page: 1, pageSize: 20, totalCount: 49 }), true);
  assert.equal(hasNextStatementPage({ page: 2, pageSize: 20, totalCount: 49 }), false);
  assert.equal(hasNextStatementPage({ page: 1, pageSize: 20, totalCount: 40 }), false);
});

test('edits between pages invalidate loaded rows even when the count stays the same', async () => {
  const { sameStatementDays } = await import('../src/pages/ledger/transaction.ts');
  const day = { bookedOn: '2026-11-25', entryCount: 20, expenseCents: 100, incomeCents: 0 };
  assert.equal(sameStatementDays([day], [{ ...day }]), true);
  for (const change of [{ expenseCents: 200 }, { bookedOn: '2026-11-26' }, { entryCount: 19 }]) {
    assert.equal(sameStatementDays([day], [{ ...day, ...change }]), false);
  }
});

test('refund date rolls over at Beijing midnight', async () => {
  const { ledgerToday } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(ledgerToday(new Date('2026-11-25T15:59:59Z')), '2026-11-25');
  assert.equal(ledgerToday(new Date('2026-11-25T16:00:00Z')), '2026-11-26');
});

test('pre-filled bill amounts can be submitted without editing', async () => {
  const { formatCents, formatCentsForInput, parseCents } =
    await import('../src/pages/ledger/transaction.ts');
  assert.equal(formatCents(750000), '7,500.00');
  assert.equal(formatCentsForInput(750000), '7500.00');
  assert.equal(parseCents(formatCentsForInput(750000)), 750000);
});

test('a short-month visible first period does not change the original due day', async () => {
  const { clampLedgerDueOn } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(clampLedgerDueOn('2025-02', 31), '2025-02-28');
  assert.equal(clampLedgerDueOn('2024-02', 31), '2024-02-29');
  assert.equal(clampLedgerDueOn('2025-03', 31), '2025-03-31');
});

test('rental payment draft is optional and only sends the bill and period', async () => {
  const { rentalPaymentExtensionEditor: editor } =
    await import('../src/pages/ledger/rental-payment-extension.ts');
  assert.equal(editor.validate({}), '');
  assert.notEqual(editor.validate({ billId: 'bill_1' }), '');
  assert.deepEqual(
    editor.toWire({ billId: 'bill_1', month: '2025-02', billName: '房租', amountCents: 250000 }),
    { billId: 'bill_1', month: '2025-02' },
  );
});

test('contract reminders use natural days, including disabled advance notices and handled occurrences', async () => {
  const { isRentalExpiryPending } = await import('../src/pages/ledger/rental-expiry.ts');
  assert.equal(isRentalExpiryPending('2025-01-07', null, 7, '2024-12-31'), true);
  assert.equal(isRentalExpiryPending('2025-01-08', null, 7, '2024-12-31'), false);
  assert.equal(isRentalExpiryPending('2025-01-08', null, 0, '2025-01-07'), false);
  assert.equal(isRentalExpiryPending('2025-01-08', null, 0, '2025-01-08'), true);
  assert.equal(isRentalExpiryPending('2025-01-08', '2025-01-08', 30, '2025-01-08'), false);
  assert.equal(isRentalExpiryPending('2025-01-09', '2025-01-08', 7, '2025-01-08'), true);
});

test('renewal starts on the next calendar day, including year-end and leap day', async () => {
  const { nextLedgerDay } = await import('../src/pages/ledger/transaction.ts');
  assert.equal(nextLedgerDay('2025-12-31'), '2026-01-01');
  assert.equal(nextLedgerDay('2024-02-28'), '2024-02-29');
  assert.equal(nextLedgerDay('2025-02-28'), '2025-03-01');
});
