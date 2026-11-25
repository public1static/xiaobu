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

// 口述金额先换算为分。含糊的简写（如“一百二”）留给用户补充。
export function spokenCents(raw) {
  const digits = '零一二三四五六七八九';
  const text = raw
    .replaceAll('两', '二')
    .replaceAll('块钱', '元')
    .replaceAll('块', '元')
    .replace(/钱$/, '');
  const decimal = text.match(/^(\d+)(?:\.(\d{1,2}))?元?$/);
  if (decimal) return Number(decimal[1]) * 100 + Number((decimal[2] || '').padEnd(2, '0'));
  const match = text.match(
    /^([零一二三四五六七八九十百千万\d]+)(?:点([零一二三四五六七八九]{1,2})元?|元(?:([零一二三四五六七八九\d])(?:毛|角)?(?:([一二三四五六七八九\d])分)?)?)?$/,
  );
  if (!match) return null;
  const digit = (c) => (digits.includes(c) ? digits.indexOf(c) : Number(c));
  // 逐段读取万以下的正规数词，拒绝单位倒置和省略单位的尾数。
  function section(s) {
    if (/^\d+$/.test(s)) return Number(s);
    if (
      !/^(?:[一二三四五六七八九]千)?(?:零?[一二三四五六七八九]百)?(?:零?[一二三四五六七八九]?十)?(?:零?[一二三四五六七八九])?$/.test(
        s,
      ) &&
      s !== '零'
    )
      return NaN;
    if (/[百千][一二三四五六七八九]$/.test(s)) return NaN;
    let total = 0,
      n = 0;
    for (const c of s) {
      const unit = { 十: 10, 百: 100, 千: 1000 }[c];
      if (unit) {
        total += (n || 1) * unit;
        n = 0;
      } else n = digit(c);
    }
    return total + n;
  }
  const parts = match[1].split('万');
  if (
    parts.length > 2 ||
    !parts[0] ||
    (parts.length === 2 && /^[一二三四五六七八九]$/.test(parts[1]))
  )
    return null;
  const whole =
    parts.length === 1 ? section(parts[0]) : section(parts[0]) * 10000 + section(parts[1]);
  const fraction = match[2]
    ? Number([...match[2]].map(digit).join('').padEnd(2, '0'))
    : (match[3] ? digit(match[3]) * 10 : 0) + (match[4] ? digit(match[4]) : 0);
  const cents = whole * 100 + fraction;
  return Number.isSafeInteger(cents) ? cents : null;
}

// 只消费能解释的记账词；查询、复合指令和含有陌生限定词的句子交回查询入口。
export function expenseIntent(input, categories, accounts, previous) {
  const text = input.normalize('NFKC').replace(/[\s，,。！!]/g, '');
  if (!previous && (text === '支出' || categories.includes(text))) return null;
  if (!text || /[？?]|多少|几笔|查询|退款|退了/.test(text)) return null;
  const aliases = {
    午饭: '餐饮',
    晚饭: '餐饮',
    早餐: '餐饮',
    吃饭: '餐饮',
    打车: '交通',
    地铁: '交通',
    公交: '交通',
    房租: '住房',
  };
  const labels = [...new Set([...categories, ...Object.keys(aliases)])].sort(
    (a, b) => b.length - a.length,
  );
  const categoryWord = labels.find((label) => text.includes(label));
  const category = categories.includes(categoryWord) ? categoryWord : aliases[categoryWord];
  let rest = text.replace(/^(?:帮我|给我)?(?:记(?:一下|一笔|个账)|记录)?/, '');
  const day = rest.match(/今天|昨天|前天/)?.[0];
  if (day) rest = rest.replace(day, '');
  const account = [...accounts].sort((a, b) => b.length - a.length).find((a) => rest.includes(a));
  if (account) rest = rest.replace('用' + account, account).replace(account, '');
  if (categoryWord) rest = rest.replace(categoryWord, '');
  const spend = /花费了?|花了?|支出|付款|支付|付了/;
  const explicit = spend.test(rest);
  // 单一消费事项可保留为备注，由用户决定分类，例如“修车花了八十元”。
  const purpose =
    !category &&
    rest.match(
      /^([\p{Script=Han}]{1,12})(?:花费了|花了)(?=[零一二两三四五六七八九十百千万\d])/u,
    )?.[1];
  if (purpose) {
    if (
      /没|未|别|不要|预计|打算|可能|如果|假如|预算|和|又|还|共|总|退|收入|转账|上周|上月|去年/.test(
        purpose,
      )
    )
      return null;
    rest = rest.slice(purpose.length);
  }
  rest = rest.replace(spend, '');
  const follow =
    previous &&
    ((!previous.category && category && rest === '') ||
      (!previous.amount && !category && !explicit && !day && !account));
  if (!category && !explicit && !follow) return null;
  const draft = {
    ...(follow ? previous : {}),
    ...(purpose ? { note: purpose } : {}),
    ...(category ? { category } : {}),
    ...(day ? { day } : {}),
    ...(account ? { account } : {}),
  };
  if (rest) {
    if (!/^[零一二两三四五六七八九十百千万点元块钱毛角分\d.]+$/.test(rest)) return null;
    const amount = spokenCents(rest);
    if (amount > 0 && Number.isSafeInteger(amount)) draft.amount = amount;
  }
  return draft;
}
