const { test } = require('node:test');
const assert = require('node:assert/strict');
test('month-end schedules, payment, existing links and restored rows stay consistent', async () => {
  const { billPeriods, dueDate, settleBill } = await import('./src/fixed-bills.mjs');
  const { remove, restore, save } = await import('./src/ledger.mjs');
  const bill = {
    id: 'rent',
    name: '房租',
    cycle: 'month',
    start: '2024-01-31',
    amount: 20000,
    account: '现金',
    category: '住房',
    paid: {},
  };
  assert.equal(dueDate(bill, '2024-02'), '2024-02-29');
  assert.equal(dueDate(bill, '2024-03'), '2024-03-31');
  assert.equal(dueDate({ ...bill, cycle: 'year', start: '2024-02-29' }, '2025-02'), '2025-02-28');
  const initial = { version: 1, rows: [], trash: [], fixedBills: [bill] };
  const payment = { amount: 20000, date: '2024-02-28', account: '现金', author: 'me' };
  let book = settleBill(initial, 'rent', '2024-02', { payment }, () => 'first').book;
  assert.equal(book.rows.length, 1);
  assert.ok(settleBill(book, 'rent', '2024-02', { payment }).error);
  assert.ok(settleBill(book, 'rent', '2024-03', { rowId: 'first' }).error);
  book = remove(book, 'first');
  assert.equal(billPeriods(book, '2024-02')[0].row, null);
  book = save(book, { ...payment, kind: 'expense', category: '住房', scope: 'personal' }, 'other');
  const rows = book.rows;
  book = settleBill(book, 'rent', '2024-02', { rowId: 'other' }).book;
  assert.equal(book.rows, rows);
  book = restore(book, 'first');
  assert.equal(billPeriods(book, '2024-02')[0].row.id, 'other');
});
test('changing future installments preserves prepaid months and pending history', async () => {
  const { revisePlan, billPeriods, nextPlanDate } = await import('./src/fixed-bills.mjs');
  const { billReminder } = await import('./src/reminder-sources.mjs');
  const bill = {
    id: 'old',
    name: '宽带',
    start: '2026-01-31',
    cycle: 'month',
    account: '',
    category: '住房',
    amount: 5000,
    paid: { '2026-12': 'payment' },
  };
  const book = { fixedBills: [bill], rows: [{ id: 'payment', kind: 'expense' }] };
  const start = nextPlanDate(bill, '2026-11-25');
  assert.equal(start, '2027-01-31');
  const next = revisePlan(book, 'old', '2026-11-25', {
    ...bill,
    id: 'new',
    name: '宽带续费',
    start,
    amount: 8000,
    paid: {},
  });
  assert.equal(billPeriods(next, '2026-12')[0].row.id, 'payment');
  assert.deepEqual(
    billPeriods(next, '2027-01').map((p) => p.bill.id),
    ['new'],
  );
  const stopped = revisePlan(book, 'old', '2026-11-25');
  assert.equal(billPeriods(stopped, '2027-01').length, 0);
  assert.ok(
    [...billReminder.pending(stopped, '2027-03-01')].every((i) => i.occurrence < '2027-01'),
  );
});
