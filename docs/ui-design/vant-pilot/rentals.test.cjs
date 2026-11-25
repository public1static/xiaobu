const test = require('node:test');
const assert = require('node:assert/strict');
test('lease binding and payment use one rent plan and one expense', async () => {
  const { storeLease } = await import('./src/rentals.mjs');
  const { billPeriods, settleBill } = await import('./src/fixed-bills.mjs');
  const initial = { version: 1, rows: [], trash: [], rentalHomes: [{ id: 'h', name: '公寓' }] };
  const l = {
    id: 'lease',
    homeId: 'h',
    party: '房东',
    start: '2026-11-01',
    end: '2027-02-28',
    rent: 250000,
    deposit: 250000,
  };
  const created = storeLease(initial, l, 'create', '', '2026-11-05', () => 'plan').book;
  const paid = settleBill(
    created,
    'plan',
    '2026-11',
    { payment: { date: '2026-11-05', amount: 250000 } },
    () => 'row',
  ).book;
  const standalone = { ...paid, leases: [] };
  const linked = storeLease(standalone, l, 'link', 'plan').book;
  assert.equal(linked.fixedBills.length, 1);
  assert.equal(linked.rows.length, 1);
  assert.equal(billPeriods(linked, '2026-11')[0].row.id, 'row');
  assert.equal(billPeriods(linked, '2027-03').length, 0);
  assert.ok(settleBill(linked, 'plan', '2026-11', { rowId: 'row' }).error);
  const edited = storeLease(linked, { ...l, rent: 260000 }, 'create', '', '2026-11-03').book;
  assert.equal(edited.fixedBills.length, 1);
  assert.equal(billPeriods(edited, '2026-11')[0].row.amount, 250000);
  assert.equal(billPeriods(edited, '2026-12')[0].bill.amount, 260000);
});
test('partial deposit return affects cash once and stays separate from rent', async () => {
  const { storeDeposit } = await import('./src/rentals.mjs');
  const { accountBalances } = await import('./src/accounts.mjs');
  const book = {
    rows: [],
    trash: [],
    accounts: [{ name: '现金', opening: 100000 }],
    leases: [{ id: 'l' }],
  };
  const d = {
    id: 'd',
    leaseId: 'l',
    kind: 'pay',
    date: '2026-11-01',
    amount: 30000,
    account: '现金',
  };
  const paid = storeDeposit(book, d).book;
  const refund = { ...d, id: 'r', kind: 'refund', amount: 12000, date: '2026-11-20' };
  const next = storeDeposit(paid, refund).book;
  assert.equal(accountBalances(next).get('现金'), 82000);
  assert.equal(next.rows.length, 0);
  assert.ok(storeDeposit(next, { ...refund, amount: 31000 }).error);
  assert.ok(storeDeposit(next, { ...d, amount: 10000 }).error);
  assert.equal(storeDeposit(next, refund).book.depositEntries.length, 2);
});
test('rent installments follow the first due month and retain linked payments', async () => {
  const { storeLease } = await import('./src/rentals.mjs');
  const { billPeriods, settleBill } = await import('./src/fixed-bills.mjs');
  const seed = { version: 1, rows: [], trash: [], rentalHomes: [{ id: 'home', name: '公寓' }] };
  for (const [cycle, multiplier, next] of [
    ['quarter', 3, '2027-02'],
    ['halfYear', 6, '2027-05'],
    ['year', 12, '2027-11'],
  ]) {
    const lease = {
      id: 'lease',
      homeId: 'home',
      party: '房东',
      start: '2026-11-01',
      end: '2028-01-31',
      rent: 280000,
      deposit: 0,
      cycle,
    };
    const book = storeLease(seed, lease, 'create', '', '2026-11-30', () => 'rent').book;
    assert.equal(billPeriods(book, '2026-12').length, 0);
    const [p] = billPeriods(book, next);
    assert.equal(p.bill.amount, 280000 * multiplier);
    const paid = settleBill(
      book,
      'rent',
      next,
      { payment: { date: p.due, amount: p.bill.amount } },
      () => 'row',
    ).book;
    const linked = storeLease({ ...paid, leases: [] }, lease, 'link', 'rent').book;
    assert.equal(billPeriods(linked, next)[0].row.id, 'row');
    assert.equal(linked.rows.length, 1);
  }
});

test('housing entry can link, move and clear one payment without duplicating expenses', async () => {
  const { rentChoices, setRentPeriod, billPeriods } = await import('./src/fixed-bills.mjs');
  const { storeLease } = await import('./src/rentals.mjs');
  const book = storeLease(
    { rows: [], trash: [], rentalHomes: [{ id: 'h', name: '公寓' }] },
    {
      id: 'l',
      homeId: 'h',
      party: '房东',
      rent: 320000,
      deposit: 0,
      start: '2026-01-01',
      end: '2026-12-31',
      cycle: 'quarter',
    },
    'create',
    '',
    '2026-01-10',
    () => 'rent',
  ).book;
  book.rows = [
    { id: 'one', kind: 'expense', scope: 'personal', amount: 940000 },
    { id: 'two', kind: 'expense', scope: 'personal', amount: 960000 },
  ];
  const choices = rentChoices(book);
  assert.deepEqual(
    choices.map((c) => c.month),
    ['2026-01', '2026-04', '2026-07', '2026-10'],
  );
  const paid = setRentPeriod(book, 'one', choices[0]).book;
  assert.equal(paid.rows, book.rows);
  assert.equal(rentChoices(paid).length, 3);
  assert.equal(rentChoices(paid, 'one').length, 4);
  assert.ok(setRentPeriod(paid, 'two', choices[0]).error);
  const moved = setRentPeriod(paid, 'one', choices[1]).book;
  assert.equal(billPeriods(moved, '2026-01')[0].row, null);
  assert.equal(billPeriods(moved, '2026-04')[0].row.amount, 940000);
  assert.deepEqual(setRentPeriod(moved, 'one', null).book.fixedBills[0].paid, {});
});
