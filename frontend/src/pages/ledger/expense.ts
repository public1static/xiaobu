export const CATEGORIES = [
  ['DINING', '餐饮'],
  ['GROCERIES', '买菜'],
  ['TRANSIT', '交通'],
  ['DAILY', '日用品'],
  ['HOUSING', '住房'],
  ['SHOPPING', '购物'],
  ['ENTERTAINMENT', '娱乐'],
  ['TRAVEL', '旅行'],
  ['HEALTH', '医疗'],
  ['EDUCATION', '教育'],
  ['OTHER', '其他支出'],
] as const;

export type CategoryCode = (typeof CATEGORIES)[number][0];

export function categoryLabel(code: string) {
  return CATEGORIES.find(([key]) => key === code)?.[1] ?? '其他支出';
}

export function parseCents(input: string): number | null {
  const value = input.trim().replace(/^\./, '0.');
  const m = /^(\d{1,8})(?:\.(\d{1,2}))?$/.exec(value);
  if (!m) return null;
  const cents = Number(m[1]) * 100 + Number((m[2] ?? '').padEnd(2, '0'));
  return cents > 0 ? cents : null;
}

export function formatCents(cents: number) {
  return (cents / 100).toFixed(2).replace(/\B(?=(\d{3})+\.)/g, ',');
}

export function localDay(date = new Date()) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-');
}

export const FIRST_LEDGER_MONTH = '1900-01';

export function resolveLedgerMonth(value: unknown, currentMonth: string) {
  return typeof value === 'string' &&
    /^\d{4}-(0[1-9]|1[0-2])$/.test(value) &&
    value >= FIRST_LEDGER_MONTH &&
    value <= currentMonth
    ? value
    : currentMonth;
}

export function shiftLedgerMonth(month: string, offset: -1 | 1) {
  const [year, monthNumber] = month.split('-').map(Number);
  const index = year * 12 + monthNumber - 1 + offset;
  return `${Math.floor(index / 12)}-${String((index % 12) + 1).padStart(2, '0')}`;
}
