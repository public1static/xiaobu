const { test } = require('node:test');
const assert = require('node:assert/strict');
const { execFileSync } = require('node:child_process');
const app = import('./src/ledger.mjs');
function rows() {
  return ['2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02'].map((date, i) => ({
    id: 'd' + i,
    date,
    kind: 'expense',
    category: '餐饮',
    amount: [1100, 1700, 8300, 1900][i],
    scope: i === 3 ? 'family' : 'personal',
  }));
}

test('single-day and cross-month ranges are inclusive; local dates stay stable at UTC+08 midnight', async () => {
  const { select, totals, validRange } = await app;
  const iso = execFileSync(
    process.execPath,
    [
      '--input-type=module',
      '-e',
      "import {localDay} from './src/ledger.mjs';const d=new Date(2026,10,1);process.stdout.write(localDay(d)+' '+d.toISOString().slice(0,10));",
    ],
    { cwd: __dirname, env: { ...process.env, TZ: 'Asia/Shanghai' }, encoding: 'utf8' },
  );
  assert.equal(iso, '2026-11-01 2026-10-31');
  assert.equal(totals(select(rows(), { period: ['2026-10-31', '2026-10-31'] })).expense, 1700);
  assert.equal(totals(select(rows(), { period: ['2026-10-31', '2026-11-01'] })).expense, 10000);
  assert.equal(validRange(['2026-11-01', '2026-10-31']), false);
});

test('a single cash gift is posted, edited, removed and restored, with no duplicate', async () => {
  const { fixture, save, remove, restore, totals, select } = await app;
  const start = fixture('2026-11-01');
  const row = {
    kind: 'expense',
    category: '随礼',
    amount: 20000,
    date: '2026-11-01',
    scope: 'personal',
    gift: { person: '林小满', distinction: '同事', contactId: 'p2', event: '婚礼' },
  };
  const after = save(start, row, 'new');
  assert.equal(after.rows.length, start.rows.length + 1);
  const edited = save(after, { ...row, amount: 23000 }, 'new');
  assert.equal(totals(edited.rows).expense - totals(start.rows).expense, 23000);
  const gone = remove(edited, 'new');
  assert.equal(
    select(gone.rows, { giftOnly: true }).some((r) => r.id === 'new'),
    false,
  );
  assert.equal(gone.trash.length, 1);
  const back = restore(gone, 'new');
  assert.deepEqual(back.rows.filter((r) => r.id === 'new')[0].gift, row.gift);
  assert.equal(restore(back, 'new'), null);
});

test('storage reads only pilot key and family search excludes gift details', async () => {
  const { KEY, read, select } = await app,
    keys = [];
  const book = read(
    {
      getItem(k) {
        keys.push(k);
        return null;
      },
    },
    '2026-11-01',
  );
  assert.deepEqual(keys, [KEY]);
  assert.notEqual(KEY, 'hezhang-complete-v1');
  const familyGift = {
    id: 'gift',
    kind: 'expense',
    category: '随礼',
    scope: 'family',
    date: '2026-11-01',
    amount: 38000,
    note: '私密备注',
    gift: { person: '私密姓名' },
  };
  assert.deepEqual(select([...book.rows, familyGift], { scope: 'family', query: '私密' }), []);
  assert.equal(select(book.rows, { scope: 'personal', query: '采购' }).length, 1);
  assert.equal(
    book.rows.some((row) => row.scope === 'family'),
    false,
  );
});

test('partial refunds offset spending on receipt date and follow the original through trash and restore', async () => {
  const { fixture, cashRows, saveRefund, totals, save, remove, restore } =
    await import('./src/ledger.mjs');
  const { accountBalances, changeAccount } = await import('./src/accounts.mjs');
  const book = fixture('2026-11-24'),
    row = book.rows[0];
  book.accounts = ['微信', '银行卡'].map((name) => ({ name, active: true, opening: 0 }));
  const refund = { id: 'r1', date: '2026-12-02', account: '银行卡', amount: 800 };
  const next = saveRefund(book, row.id, refund);
  assert.equal(saveRefund(next, row.id, { ...refund, id: 'r2', amount: row.amount }), null);
  assert.equal(save(next, { ...row, amount: 500 }, row.id), null);
  const delta = cashRows(next.rows).filter((r) => r.date === refund.date);
  assert.deepEqual(totals(delta), { income: 0, expense: -800, balance: 800 });
  assert.equal(accountBalances(next).get('银行卡') - accountBalances(book).get('银行卡'), 800);
  const renamed = changeAccount(next, '银行卡', '工资卡', true).book;
  assert.equal(renamed.rows[0].refunds[0].account, '工资卡');
  const deleted = remove(renamed, row.id);
  assert.equal(
    cashRows(deleted.rows).some((r) => r.originalId === row.id),
    false,
  );
  assert.equal(restore(deleted, row.id).rows[0].refunds[0].amount, 800);
});
