const { test } = require('node:test');
const assert = require('node:assert/strict');
const model = import('./src/CategoryCatalog.mjs');
const ledger = import('./src/ledger.mjs');
const storage = () => ({
  contents: {},
  getItem(k) {
    return Object.hasOwn(this.contents, k) ? this.contents[k] : null;
  },
  setItem(k, v) {
    this.contents[k] = v;
  },
});
test('custom labels are normalized and duplicate checks ignore width, spacing and case per kind', async () => {
  const C = await model;
  const start = C.defaultCategoryCatalog();
  const first = C.addCategory(start, 'expense', '  Cafe   Snack  ');
  assert.ok(first.value, first.error);
  assert.equal(first.label, 'Cafe Snack');
  assert.match(C.addCategory(first.value, 'expense', 'ＣＡＦＥＳＮＡＣＫ').error, /同名/);
  assert.ok(C.addCategory(first.value, 'income', 'Cafe Snack').value);
  assert.ok(C.addCategory(first.value, 'expense', '买 菜').error);
  for (const invalid of ['', '　', '名字\u200f', '坏\n名字', '一'.repeat(17)])
    assert.ok(C.addCategory(first.value, 'expense', invalid).error);
  assert.ok(!start.expense.includes('Cafe Snack'));
});
test('reorder persists per kind without writing the pilot book or renaming categories', async () => {
  const C = await model,
    L = await ledger,
    store = storage();
  const book = L.fixture('2026-11-01');
  store.setItem(L.KEY, JSON.stringify(book));
  const origin = store.getItem(L.KEY);
  const d = C.defaultCategoryCatalog(),
    from = d.expense.indexOf('买菜');
  const outcome = C.reorderCategories(d, 'expense', from, 0);
  assert.equal(outcome.expense[0], '买菜');
  assert.deepEqual(outcome.income, d.income);
  assert.ok(C.persistCategoryCatalog(store, outcome).value);
  assert.deepEqual(C.loadCategoryCatalog(store), outcome);
  assert.equal(C.reorderCategories(outcome, 'income', -1, 0), outcome);
  assert.equal(store.getItem(L.KEY), origin);
  assert.ok(
    C.persistCategoryCatalog(
      {
        setItem() {
          throw Error();
        },
      },
      outcome,
    ).error,
  );
});
test('custom category ledger rows survive reload and trash restoration without depending on category sort order', async () => {
  const C = await model,
    L = await ledger,
    store = storage(),
    date = '2026-11-01';
  const sample = L.fixture(date);
  store.setItem(L.KEY, JSON.stringify(sample));
  assert.deepEqual(L.read(store, date), sample);
  const added = C.addCategory(C.defaultCategoryCatalog(), 'expense', '咖啡豆');
  let reopened = L.save(
    L.read(store, date),
    { kind: 'expense', category: added.label, amount: 1099, date, scope: 'personal' },
    'fresh',
  );
  assert.ok(reopened);
  assert.equal(reopened.rows[0].category, '咖啡豆');
  const reordered = C.reorderCategories(added.value, 'expense', added.value.expense.length - 1, 0);
  C.persistCategoryCatalog(store, reordered);
  store.setItem(L.KEY, JSON.stringify(reopened));
  reopened = L.read(store, date);
  assert.equal(reopened.rows[0].id, 'fresh');
  const deleted = L.remove(reopened, 'fresh');
  store.setItem(L.KEY, JSON.stringify(deleted));
  assert.equal(L.restore(L.read(store, date), 'fresh').rows[0].category, '咖啡豆');
  assert.equal(
    L.save(
      sample,
      { kind: 'expense', category: ' ', amount: 99, date, scope: 'personal' },
      'invalid',
    ),
    null,
  );
});
