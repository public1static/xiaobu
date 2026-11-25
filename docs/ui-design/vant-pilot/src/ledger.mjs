export const KEY = 'hezhang-vant-pilot-v1';
export { CATEGORIES } from './CategoryCatalog.mjs';
import { validCategoryLabel, categoryName } from './CategoryCatalog.mjs';
export const CONTACTS = [
  { id: 'p1', name: '林小满', distinction: '朋友' },
  { id: 'p2', name: '林小满', distinction: '同事' },
  { id: 'p3', name: '周宁', distinction: '亲友' },
];
const str = (v, max) =>
  String(v ?? '')
    .trim()
    .slice(0, max);
export const localDay = (d) =>
  [
    d.getFullYear(),
    String(d.getMonth() + 1).padStart(2, '0'),
    String(d.getDate()).padStart(2, '0'),
  ].join('-');
export function asDate(s) {
  const [y, m, d] = String(s).split('-').map(Number);
  return new Date(y, m - 1, d);
}
export const validDay = (s) =>
  typeof s === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(s) && localDay(asDate(s)) === s;
export const validRange = (range) =>
  Array.isArray(range) && range.length === 2 && range.every(validDay) && range[0] <= range[1];
export function monthRange(day) {
  const d = asDate(day),
    y = d.getFullYear(),
    m = d.getMonth();
  return [localDay(new Date(y, m, 1)), localDay(new Date(y, m + 1, 0))];
}
export const amountText = (n) =>
  (n / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
export const amountSigned = (r) =>
  (r.kind === 'income' || r.originalId ? '+' : '−') + '¥' + amountText(Math.abs(r.amount));
export function fixture(today = localDay(new Date())) {
  const on = (n) => {
    const d = asDate(today);
    d.setDate(d.getDate() + n);
    return localDay(d);
  };
  const row = (id, d, kind, category, amount, note, account, scope, gift) => ({
    id,
    date: on(d),
    kind,
    category,
    amount,
    note,
    account,
    scope,
    ...(gift ? { gift } : {}),
  });
  return {
    version: 1,
    rows: [
      row('sample-1', 0, 'expense', '餐饮', 3260, '午饭 · 牛肉面', '微信', 'personal'),
      row('sample-2', 0, 'expense', '交通', 650, '地铁通勤', '支付宝', 'personal'),
      row('sample-3', -1, 'expense', '买菜', 18640, '周末采购', '微信', 'personal'),
      row('sample-4', -1, 'expense', '随礼', 50000, '新婚贺礼', '微信', 'personal', {
        person: '林小满',
        event: '婚礼',
        contactId: 'p1',
        distinction: '朋友',
      }),
      row('sample-5', -1, 'income', '工资', 1260000, '本月工资到账', '银行卡', 'personal'),
      row('sample-6', -2, 'expense', '住房', 238000, '房租', '银行卡', 'personal'),
      row('sample-7', -2, 'income', '收礼', 30000, '', '微信', 'personal', {
        person: '周宁',
        event: '乔迁',
        contactId: 'p3',
        distinction: '亲友',
      }),
    ],
    trash: [],
  };
}
// 退款属于原支出，累计金额不能超过原付金额。
export const refunded = (row) => (row.refunds ?? []).reduce((sum, item) => sum + item.amount, 0);
function validRefunds(row) {
  if (row.refunds === undefined) return true;
  return (
    Array.isArray(row.refunds) &&
    (!row.refunds.length || row.kind === 'expense') &&
    row.refunds.every(
      (item) =>
        item &&
        typeof item.id === 'string' &&
        item.id &&
        validDay(item.date) &&
        item.date >= row.date &&
        Number.isSafeInteger(item.amount) &&
        item.amount > 0 &&
        typeof item.account === 'string' &&
        item.account.length <= 40,
    ) &&
    new Set(row.refunds.map((item) => item.id)).size === row.refunds.length &&
    refunded(row) <= row.amount
  );
}
// 在实际到账日冲减原分类支出；原单和退款分别使用各自的账户。
export function cashRows(rows) {
  return rows.flatMap((row) => [
    row,
    ...(row.refunds ?? []).map((item) => ({
      ...item,
      id: JSON.stringify([row.id, item.id]),
      originalId: row.id,
      kind: 'expense',
      scope: row.scope,
      category: row.category,
      note: '退款',
      amount: -item.amount,
    })),
  ]);
}
// 同一退款编号只保留一条，重试或修改金额都重新检查原单可退余额。
export function saveRefund(book, id, item) {
  const original = book.rows.find((row) => row.id === id);
  if (original?.kind !== 'expense') return null;
  const refunds = [...(original.refunds ?? []).filter((r) => r.id !== item.id), item];
  const updated = { ...original, refunds };
  return validRefunds(updated)
    ? { ...book, rows: book.rows.map((row) => (row.id === id ? updated : row)) }
    : null;
}
export function rowValid(r) {
  return (
    r &&
    typeof r.id === 'string' &&
    ['expense', 'income'].includes(r.kind) &&
    Number.isSafeInteger(r.amount) &&
    r.amount > 0 &&
    validDay(r.date) &&
    validCategoryLabel(r.category) &&
    ['personal', 'family'].includes(r.scope) &&
    validRefunds(r)
  );
}
// 旧流水与最近删除中的兜底分类沿用当前名称。
export function upgradeBookCategories(book) {
  const upgrade = (row) =>
    categoryName(row.kind, row.category) === row.category
      ? row
      : { ...row, category: categoryName(row.kind, row.category) };
  return { ...book, rows: book.rows.map(upgrade), trash: book.trash.map(upgrade) };
}
export function read(storage, today) {
  try {
    const raw = storage.getItem(KEY);
    if (raw === null) return fixture(today);
    const book = JSON.parse(raw);
    if (
      book.version !== 1 ||
      !Array.isArray(book.rows) ||
      !Array.isArray(book.trash) ||
      ![...book.rows, ...book.trash].every(rowValid) ||
      new Set([...book.rows, ...book.trash].map((r) => r.id)).size !==
        book.rows.length + book.trash.length
    )
      throw new Error();
    return upgradeBookCategories(book);
  } catch {
    return fixture(today);
  }
}
export function select(
  rows,
  { period, start, end, scope = 'all', query = '', giftOnly = false } = {},
) {
  const bounds = period ?? [start, end];
  const terms = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  return rows
    .filter(
      (r) =>
        (scope === 'all' || r.scope === scope) &&
        (!bounds[0] || r.date >= bounds[0]) &&
        (!bounds[1] || r.date <= bounds[1]) &&
        (!giftOnly || ['收礼', '随礼'].includes(r.category)) &&
        terms.every((t) =>
          [
            r.category,
            scope === 'family' && r.gift && !r.gift.shared ? '' : r.note,
            r.account,
            r.date,
            scope === 'family' && !r.gift?.shared ? '' : r.gift?.person,
            scope === 'family' && !r.gift?.shared ? '' : r.gift?.event,
          ]
            .join(' ')
            .toLowerCase()
            .includes(t),
        ),
    )
    .sort((a, b) => b.date.localeCompare(a.date));
}
export function totals(rows) {
  return rows.reduce(
    (t, r) => {
      t[r.kind] += r.amount;
      t.balance += (r.kind === 'income' ? 1 : -1) * r.amount;
      return t;
    },
    { income: 0, expense: 0, balance: 0 },
  );
}
export function groups(rows) {
  const grouped = new Map();
  for (const r of rows) {
    const list = grouped.get(r.date) || [];
    list.push(r);
    grouped.set(r.date, list);
  }
  return Array.from(grouped, ([day, list]) => ({ day, rows: list, totals: totals(list) }));
}
export function save(book, input, id) {
  const raw = input?.gift;
  const gift = raw && {
    ...(raw.shared === true ? { shared: true } : {}),
    ...(raw.listId ? { listId: str(raw.listId, 100) } : {}),
    person: str(raw.person, 40),
    event: str(raw.event, 60),
    contactId: str(raw.contactId, 100),
    distinction: str(raw.distinction, 40),
  };
  const row = {
    id: String(id ?? ''),
    kind: input.kind,
    amount: input.amount,
    date: input.date,
    scope: input.scope,
    category: input.category,
    note: str(input.note, 100),
    account: str(input.account, 40),
  };
  if (input.author) row.author = str(input.author, 40);
  if (input.homeId) {
    row.homeId = str(input.homeId, 100);
    if (input.contractId) row.contractId = str(input.contractId, 100);
  }
  const original = book.rows.find((r) => r.id === row.id);
  if (original?.refunds?.length) row.refunds = original.refunds;
  if (!rowValid(row) || !row.id || book.trash.some((r) => r.id === row.id)) return null;
  if (['收礼', '随礼'].includes(row.category) && gift && Object.values(gift).some(Boolean))
    row.gift = gift;
  if (row.gift?.listId && !['daily-in', 'daily-out'].includes(row.gift.listId)) {
    const list = book.giftLists?.find((item) => item.id === row.gift.listId);
    if (
      !list ||
      list.archived ||
      list.kind !== row.kind ||
      (list.scope || 'personal') !== row.scope
    )
      return null;
  }
  const rows = book.rows.some((r) => r.id === row.id)
    ? book.rows.map((r) => (r.id === row.id ? row : r))
    : [row, ...book.rows];
  return { ...book, rows };
}
export function remove(book, id) {
  const row = book.rows.find((r) => r.id === id);
  return row
    ? { ...book, rows: book.rows.filter((r) => r.id !== id), trash: [row, ...book.trash] }
    : null;
}
export function restore(book, id) {
  const row = book.trash.find((r) => r.id === id);
  return row && !book.rows.some((r) => r.id === id)
    ? { ...book, rows: [row, ...book.rows], trash: book.trash.filter((r) => r.id !== id) }
    : null;
}
