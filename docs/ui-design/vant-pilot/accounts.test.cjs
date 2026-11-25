const test = require('node:test');
const assert = require('node:assert/strict');

test('transfers conserve cash across both accounts and leave income and expenses untouched', async () => {
  const { changeAccount, accountBalances, saveTransfer } = await import('./src/accounts.mjs');
  const { totals } = await import('./src/ledger.mjs');
  let book = { rows: [{ account: '微信', kind: 'expense', amount: 500 }], trash: [] };
  assert.equal(accountBalances(book).get('微信'), null);
  book = changeAccount(book, '微信', '微信', true, { opening: 10500 }).book;
  book = changeAccount(book, '银行卡', '工资卡', true, { opening: 0 }).book;
  const t = {
    id: 'transfer-1',
    from: '微信',
    to: '工资卡',
    amount: 3000,
    date: '2026-11-23',
    scope: 'personal',
  };
  book = saveTransfer(book, t).book;
  assert.equal(accountBalances(book).get('微信'), 7000);
  assert.equal(accountBalances(book).get('工资卡'), 3000);
  assert.deepEqual(totals(book.rows), { income: 0, expense: 500, balance: -500 });
  book = saveTransfer(book, { ...t, amount: 2000 }).book;
  assert.equal(book.transfers.length, 1);
  assert.equal(accountBalances(book).get('工资卡'), 2000);
  book = changeAccount(book, '工资卡', '储蓄卡', false).book;
  assert.equal(book.transfers[0].to, '储蓄卡');
  assert.equal(accountBalances(book).get('储蓄卡'), 2000);
  assert.ok(saveTransfer(book, { ...t, id: 'new-transfer', to: '储蓄卡' }).error);
  assert.ok(saveTransfer(book, { ...t, to: t.from }).error);
});

test('current debt and fund valuations absorb history once and track subsequent transfers', async () => {
  const { changeAccount, openingForCurrent, accountBalances, saveTransfer } =
    await import('./src/accounts.mjs');
  let book = { rows: [{ account: '京东白条', kind: 'expense', amount: 20000 }], trash: [] };
  book = changeAccount(book, '京东白条', '京东白条', true, {
    type: '京东白条',
    opening: openingForCurrent(book, '京东白条', -80000),
  }).book;
  book = changeAccount(book, '', '基金', true, { type: '基金', opening: 100000 }).book;
  assert.equal(accountBalances(book).get('京东白条'), -80000);
  book = saveTransfer(book, {
    id: 'repay',
    from: '银行卡',
    to: '京东白条',
    amount: 30000,
    date: '2026-11-23',
    scope: 'personal',
  }).book;
  book = saveTransfer(book, {
    id: 'buy',
    from: '银行卡',
    to: '基金',
    amount: 20000,
    date: '2026-11-23',
    scope: 'personal',
  }).book;
  assert.equal(accountBalances(book).get('京东白条'), -50000);
  assert.equal(accountBalances(book).get('基金'), 120000);
  assert.equal(book.rows.length, 1);
});
