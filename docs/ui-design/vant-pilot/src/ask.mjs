import { amountText, CATEGORIES, totals } from './ledger.mjs';

export function answerQuestion(question, rows) {
  const q = String(question ?? '')
    .trim()
    .replace(/[？?。！!]+$/, '');
  if (!q) return '';
  if (
    /今天|昨天|前天|明天|这周|本周|上周|这个月|本月|上月|上个月|去年|今年|\d+\s*[月日号年]|个人账|家庭账/.test(
      q,
    )
  )
    return '请先在流水里选好日期和账本，再回来查。';
  if (
    /最多|最少|排行|平均|预测|推荐|建议|趋势|占比|同比|环比|分别|哪笔|哪些|几笔|预算|余额|转账|退款|谁/.test(
      q,
    )
  )
    return '暂时只能查当前范围的收入、支出、结余，或某一分类的合计。';
  const catalog = [
    ...new Set([...CATEGORIES.expense, ...CATEGORIES.income, ...rows.map((r) => r.category)]),
  ]
    .filter(Boolean)
    .sort((a, b) => b.length - a.length);
  const category = catalog.find((name) => q.includes(name));
  const categoryKind =
    category &&
    (rows.find((r) => r.category === category)?.kind ??
      (CATEGORIES.income.includes(category) ? 'income' : 'expense'));
  const qualifier = q.replace(
    /这段时间|当前范围|当前|一共|总共|总计|合计|是多少|是多少元|多少|了|有|是|的|我|看看|查一下|查询|请问|请|给我|金额|钱|元|人民币|\s/g,
    '',
  );
  const rest = category ? qualifier.replace(category, '') : qualifier;
  const type = /^(收入|总收入|挣|赚)$/.test(rest)
    ? 'income'
    : /^(支出|总支出|花)$/.test(rest)
      ? 'expense'
      : /^(结余|净额)$/.test(rest)
        ? 'balance'
        : rest === '收支'
          ? 'all'
          : rest === '' && category
            ? categoryKind
            : null;
  if (!type) return '暂时只能查当前范围的收入、支出、结余，或某一分类的合计。';
  if (category && ['income', 'expense'].includes(type) && type !== categoryKind)
    return '这个分类和查询类型不一致。可以直接问「' + category + '多少」。';
  const list = category ? rows.filter((row) => row.category === category) : rows;
  const t = totals(list);
  if (type === 'all')
    return (
      (category ? category + '：' : '') +
      '收入 ¥' +
      amountText(t.income) +
      '，支出 ¥' +
      amountText(t.expense) +
      '，结余 ¥' +
      amountText(t.balance) +
      '。'
    );
  return (
    (category ? category : '') +
    { income: '收入', expense: '支出', balance: '结余' }[type] +
    ' ¥' +
    amountText(t[type]) +
    '。'
  );
}

export {
  expenseIntent,
  spokenCents,
} from '../../../../frontend/src/pages/assistant/expense-intent.mjs';
