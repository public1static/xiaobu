const test = require('node:test');
const assert = require('node:assert/strict');

test('family gift keeps one identity through corrections, deletion, migration and CSV export', async () => {
  const { fixture, save, remove, restore, read, KEY } = await import('./src/ledger.mjs');
  const { makeBackup, restoreBackup, giftCsv } = await import('./src/backup.mjs');
  const { accountBalances } = await import('./src/accounts.mjs');
  const { CATEGORY_KEY } = await import('./src/CategoryCatalog.mjs');
  const { GIFT_CONTACTS_KEY } = await import('./src/gift-options.mjs');
  const store = () => {
    const map = new Map();
    return {
      getItem: (k) => map.get(k) ?? null,
      setItem: (k, v) => map.set(k, v),
      removeItem: (k) => map.delete(k),
    };
  };
  const source = store(),
    target = store(),
    today = '2026-11-23';
  const list = { id: 'banquet', name: '家庭礼单', scope: 'family', kind: 'expense', date: today };
  let book = {
    ...fixture(today),
    giftLists: [list],
    accounts: [{ name: '我的微信', active: true, opening: 100000 }],
  };
  source.setItem(
    GIFT_CONTACTS_KEY,
    JSON.stringify({
      version: 1,
      contacts: [{ id: 'relative', name: '二姨', distinction: '', household: '二姨家' }],
    }),
  );
  const row = {
    date: today,
    kind: 'expense',
    category: '随礼',
    scope: 'family',
    account: '我的微信',
    amount: 60000,
    gift: { person: '二姨', contactId: 'relative', shared: true, listId: list.id },
  };
  assert.equal(save(book, { ...row, scope: 'personal' }, 'gift'), null);
  book = save(book, row, 'gift');
  book = save(book, { ...row, amount: 80000 }, 'gift');
  assert.equal(accountBalances(book).get('我的微信'), 20000);
  book = remove(book, 'gift');
  assert.equal(accountBalances(book).get('我的微信'), 100000);
  const backup = makeBackup(source, book);
  restoreBackup(target, JSON.parse(JSON.stringify(backup)));
  assert.deepEqual(read(target, today), book);
  assert.ok(target.getItem(CATEGORY_KEY));
  assert.equal(target.getItem(GIFT_CONTACTS_KEY), source.getItem(GIFT_CONTACTS_KEY));
  book = restore(read(target, today), 'gift');
  const linked = book.rows.filter((r) => r.gift?.listId === list.id);
  assert.equal(linked.length, 1);
  assert.equal(linked[0].gift.contactId, 'relative');
  assert.match(giftCsv(list, linked), /家庭账/);
  assert.match(giftCsv(list, linked), /800\.00/);
  const before = target.getItem(KEY);
  assert.throws(() => restoreBackup(target, { ...backup, book: { ...book, rows: [row] } }));
  assert.equal(target.getItem(KEY), before);
});
