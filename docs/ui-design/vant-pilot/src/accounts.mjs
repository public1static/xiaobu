import { cashRows, validDay } from './ledger.mjs';

// 首次使用时保留原来的账户选项，历史流水和回收站里的自定义账户一并纳入。
export function readAccounts(book) {
  const catalog =
    book.accounts ?? ['微信', '支付宝', '银行卡', '现金'].map((name) => ({ name, active: true }));
  const byName = new Map(catalog.map((account) => [account.name, account]));
  for (const row of cashRows([...book.rows, ...book.trash])) {
    if (row.account && !byName.has(row.account))
      byName.set(row.account, { name: row.account, active: true });
  }
  return [...byName.values()];
}

// 新增和改名共用同一套全半角、大小写及重名判断。
export function accountNameError(accounts, original, input) {
  const name = input.trim();
  if (!name || name.length > 40) return '请输入 1 到 40 字的账户名称。';
  const key = (value) => value.normalize('NFKC').toLowerCase();
  return accounts.some((a) => a.name !== original && key(a.name) === key(name))
    ? '已有同名账户。'
    : '';
}

// 名称与启用状态随整本账一次保存，保证恢复已删除流水后也使用新名称。
export function changeAccount(book, original, input, active, settings = {}) {
  const name = input.trim();
  const accounts = readAccounts(book);
  const nameError = accountNameError(accounts, original, name);
  if (nameError) return { field: 'name', error: nameError };
  if (original && !accounts.some((a) => a.name === original)) return { error: '账户已不存在。' };
  const item = { ...accounts.find((a) => a.name === original), ...settings, name, active };
  if (item.opening != null && !Number.isSafeInteger(item.opening))
    return { field: 'balance', error: '请输入有效金额，最多两位小数。' };
  const updated = original
    ? accounts.map((a) => (a.name === original ? item : a))
    : [...accounts, item];
  const next = { ...book, accounts: updated };
  if (original && original !== name) {
    const rename = (row) => ({
      ...row,
      account: row.account === original ? name : row.account,
      ...(row.refunds
        ? {
            refunds: row.refunds.map((r) => (r.account === original ? { ...r, account: name } : r)),
          }
        : {}),
    });
    next.rows = book.rows.map(rename);
    next.trash = book.trash.map(rename);
    if (book.depositEntries)
      next.depositEntries = book.depositEntries.map((d) =>
        d.account === original ? { ...d, account: name } : d,
      );
    if (book.fixedBills)
      next.fixedBills = book.fixedBills.map((bill) =>
        bill.account === original ? { ...bill, account: name } : bill,
      );
    if (book.transfers)
      next.transfers = book.transfers.map((t) => ({
        ...t,
        from: t.from === original ? name : t.from,
        to: t.to === original ? name : t.to,
      }));
  }
  return { book: next };
}

// 一次遍历计算各账户的全账本余额；缺少期初数据时保留未知状态。
export function accountBalances(book, movementsOnly = false) {
  const balances = new Map(
    readAccounts(book).map((a) => [a.name, movementsOnly ? 0 : (a.opening ?? null)]),
  );
  function apply(name, delta) {
    if (balances.get(name) != null) balances.set(name, balances.get(name) + delta);
  }
  for (const row of cashRows(book.rows))
    apply(row.account, row.kind === 'income' ? row.amount : -row.amount);
  for (const t of book.transfers ?? []) {
    apply(t.from, -t.amount);
    apply(t.to, t.amount);
  }
  // 押金是可退还的款项，只计入账户资金变化。
  for (const d of book.depositEntries || [])
    apply(d.account, d.kind === 'pay' ? -d.amount : d.amount);
  return balances;
}

// 转账两端使用同一条记录，修正金额时同步影响两边且不计入经营收支。
export function saveTransfer(book, transfer) {
  const items = book.transfers ?? [];
  const previous = items.find((t) => t.id === transfer.id);
  const accounts = readAccounts(book);
  const available = (name, old) =>
    accounts.some((a) => a.name === name && (a.active || name === old));
  if (
    !transfer.from ||
    transfer.from === transfer.to ||
    !available(transfer.from, previous?.from) ||
    !available(transfer.to, previous?.to)
  )
    return { error: '请选择两个不同的可用账户。' };
  if (
    !Number.isSafeInteger(transfer.amount) ||
    transfer.amount <= 0 ||
    !validDay(transfer.date) ||
    !['personal', 'family'].includes(transfer.scope)
  )
    return { error: '请检查金额、日期和账本。' };
  return {
    book: {
      ...book,
      transfers: previous
        ? items.map((t) => (t.id === transfer.id ? transfer : t))
        : [transfer, ...items],
    },
  };
}

// 用用户今天看到的金额扣除已登记的变动，避免历史流水重复计算。
export function openingForCurrent(book, name, current) {
  return current == null ? null : current - (accountBalances(book, true).get(name) ?? 0);
}
