const { test } = require('node:test');
const assert = require('node:assert/strict');
const init = Promise.all([import('./src/ask.mjs'), import('./src/ledger.mjs')]);
const rows = [
  {
    id: '1',
    kind: 'expense',
    amount: 1700,
    date: '2026-10-31',
    category: '餐饮',
    scope: 'family',
    note: '餐馆',
  },
  {
    id: '2',
    kind: 'expense',
    amount: 8300,
    date: '2026-11-01',
    category: '餐饮',
    scope: 'personal',
    note: '夜宵',
  },
  { id: '3', kind: 'income', amount: 12345, date: '2026-10-31', category: '兼职', scope: 'family' },
  {
    id: '4',
    kind: 'expense',
    amount: 2000,
    date: '2026-10-31',
    category: '餐饮招待',
    scope: 'family',
  },
];
test('scope and date use selected rows instead of ledger text search', async () => {
  const [{ answerQuestion }, { select }] = await init;
  const applied = select(rows, { period: ['2026-10-31', '2026-10-31'], scope: 'family' });
  assert.equal(answerQuestion('餐饮花了多少？', applied), '餐饮支出 ¥17.00。');
  assert.equal(answerQuestion('这段时间的支出', applied), '支出 ¥37.00。');
  assert.equal(
    answerQuestion('这段时间的收支', applied),
    '收入 ¥123.45，支出 ¥37.00，结余 ¥86.45。',
  );
  assert.equal(answerQuestion('餐饮招待多少', applied), '餐饮招待支出 ¥20.00。');
});
test('does not falsely answer explicit different time, account or unsupported computations', async () => {
  const [{ answerQuestion }] = await init;
  for (const q of ['上个月花了多少', '今天收入多少', '家庭账的支出'])
    assert.match(answerQuestion(q, rows), /先在流水里选好日期和账本/);
  for (const q of ['微信支出', '餐饮平均支出', '给我一些攒钱建议'])
    assert.match(answerQuestion(q, rows), /暂时只能/);
});
test('spoken expenses keep the known fields across a clarification', async () => {
  const { expenseIntent } = await import('./src/ask.mjs');
  const parse = (text, previous) => expenseIntent(text, ['买菜', '交通'], ['微信'], previous);
  assert.deepEqual(parse('昨天微信买菜花了六十八块'), {
    day: '昨天',
    account: '微信',
    category: '买菜',
    amount: 6800,
  });
  assert.equal(parse('打车花了三十二块五').amount, 3250);
  assert.deepEqual(parse('六十八元', parse('昨天买菜')), {
    category: '买菜',
    day: '昨天',
    amount: 6800,
  });
  assert.deepEqual(parse('交通', parse('支出八十元')), { category: '交通', amount: 8000 });
  assert.equal(parse('买菜花了多少'), null);
  assert.deepEqual(parse('交通', parse('修车花了八十元')), {
    note: '修车',
    category: '交通',
    amount: 8000,
  });
  assert.equal(parse('支出'), null);
  assert.equal(parse('买菜一百二').amount, undefined);
});
