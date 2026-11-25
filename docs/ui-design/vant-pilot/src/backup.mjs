import { validTask } from './manual-reminders.mjs';
import { validLease, storeDeposit } from './rentals.mjs';
import { validFixedBill } from './fixed-bills.mjs';
import { KEY, rowValid, upgradeBookCategories, validDay, localDay } from './ledger.mjs';
import {
  CATEGORY_KEY,
  defaultCategoryCatalog,
  upgradeCatalog,
  validate as validCatalog,
} from './CategoryCatalog.mjs';
import { GIFT_CONTACTS_KEY, contactValid } from './gift-options.mjs';

// 备份只包含合账自己的三个存储区。
export function makeBackup(storage, book) {
  const load = (key, initial) => JSON.parse(storage.getItem(key) ?? JSON.stringify(initial));
  return validateBackup({
    format: '合账',
    version: 1,
    created: new Date().toISOString(),
    book,
    categories: load(CATEGORY_KEY, defaultCategoryCatalog()),
    directory: load(GIFT_CONTACTS_KEY, { version: 1, contacts: [] }),
  });
}

// 恢复前校验各目录、重复编号及金额，避免导入后被当作新账本。
export function validateBackup(data) {
  const fail = () => {
    throw new Error('文件内容不完整或版本不支持，无法恢复。');
  };
  const unique = (items, key) =>
    Array.isArray(items) &&
    items.every((item) => item && typeof item[key] === 'string' && item[key].trim()) &&
    new Set(items.map((i) => i[key])).size === items.length;
  const b = data?.book;
  if (
    data?.format !== '合账' ||
    data.version !== 1 ||
    b?.version !== 1 ||
    !Array.isArray(b.rows) ||
    !Array.isArray(b.trash)
  )
    fail();
  const rows = [...b.rows, ...b.trash];
  if (
    !unique(rows, 'id') ||
    !rows.every(rowValid) ||
    !validCatalog(upgradeCatalog(data.categories))
  )
    fail();
  if (
    b.manualReminders !== undefined &&
    (!unique(b.manualReminders, 'id') || !b.manualReminders.every(validTask))
  )
    fail();
  if (b.fixedBills !== undefined) {
    if (!unique(b.fixedBills, 'id') || !b.fixedBills.every(validFixedBill)) fail();
    const links = b.fixedBills.flatMap((bill) => Object.values(bill.paid));
    if (
      new Set(links).size !== links.length ||
      links.some((id) => !rows.some((row) => row.id === id))
    )
      fail();
  }
  if (
    b.rentalHomes !== undefined &&
    (!unique(b.rentalHomes, 'id') ||
      !b.rentalHomes.every(
        (h) => typeof h.name === 'string' && h.name.trim() && h.name.length <= 40,
      ))
  )
    fail();
  if (b.leases !== undefined) {
    if (
      !unique(b.leases, 'id') ||
      !b.leases.every(
        (l) =>
          validLease(l) &&
          b.rentalHomes?.some((h) => h.id === l.homeId) &&
          (!l.billId ||
            b.fixedBills?.some((p) => p.id === l.billId && p.cycle === (l.cycle ?? 'month'))),
      )
    )
      fail();
    const bound = b.leases.map((l) => l.billId).filter(Boolean);
    if (new Set(bound).size !== bound.length) fail();
  }
  if (b.depositEntries !== undefined) {
    if (!unique(b.depositEntries, 'id')) fail();
    if (b.depositEntries.some((d) => storeDeposit(b, d).error)) fail();
  }
  if (
    b.giftReasons !== undefined &&
    (!Array.isArray(b.giftReasons) ||
      !b.giftReasons.every((n) => typeof n === 'string' && n.trim() && n.length <= 60) ||
      new Set(b.giftReasons).size !== b.giftReasons.length)
  )
    fail();
  const people = data.directory?.contacts;
  if (
    data.directory?.version !== 1 ||
    !unique(people, 'id') ||
    people.length > 1500 ||
    !people.every(contactValid)
  )
    fail();
  if (
    b.accounts !== undefined &&
    (!unique(b.accounts, 'name') ||
      !b.accounts.every(
        (a) =>
          typeof a.active === 'boolean' && (a.opening == null || Number.isSafeInteger(a.opening)),
      ))
  )
    fail();
  if (
    b.transfers !== undefined &&
    (!unique(b.transfers, 'id') ||
      !b.transfers.every(
        (t) =>
          typeof t.from === 'string' &&
          typeof t.to === 'string' &&
          t.from !== t.to &&
          validDay(t.date) &&
          Number.isSafeInteger(t.amount) &&
          t.amount > 0 &&
          ['personal', 'family'].includes(t.scope),
      ))
  )
    fail();
  if (
    b.giftLists !== undefined &&
    (!unique(b.giftLists, 'id') ||
      !b.giftLists.every(
        (l) =>
          typeof l.name === 'string' &&
          l.name.trim() &&
          (l.date === undefined || l.date === '' || validDay(l.date)) &&
          (l.defaultReason === undefined ||
            (typeof l.defaultReason === 'string' && l.defaultReason.length <= 60)) &&
          ['income', 'expense'].includes(l.kind) &&
          ['personal', 'family'].includes(l.scope ?? 'personal'),
      ))
  )
    fail();
  return { ...data, book: upgradeBookCategories(b), categories: upgradeCatalog(data.categories) };
}

// 遇到容量或权限错误时回滚已写入的存储区。
export function restoreBackup(storage, data) {
  data = validateBackup(data);
  const entries = [
    [CATEGORY_KEY, data.categories],
    [GIFT_CONTACTS_KEY, data.directory],
    [KEY, data.book],
  ];
  const previous = [];
  try {
    for (const [key, value] of entries) {
      const old = storage.getItem(key);
      storage.setItem(key, JSON.stringify(value));
      previous.push([key, old]);
    }
  } catch {
    for (const [key, old] of previous.reverse())
      old === null ? storage.removeItem(key) : storage.setItem(key, old);
    throw new Error('本机空间或存储权限不足，恢复失败。');
  }
}

// 下载操作始终在浏览器本地完成。
export function download(name, content, type) {
  const url = URL.createObjectURL(new Blob([content], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 3000);
}

// 转义 CSV 单元格，同时阻止姓名、备注被表格软件解释为公式。
export function giftCsv(list, rows) {
  const lines = [
    ['礼单', '账本', '日期', '姓名', '关系', '方向', '金额（元）', '账户', '事由', '备注'],
  ];
  let total = 0;
  for (const r of rows) {
    total += r.amount;
    lines.push([
      list.name,
      r.scope === 'family' ? '家庭账' : '个人账',
      r.date,
      r.gift?.person,
      r.gift?.distinction,
      r.kind === 'income' ? '收礼' : '送礼',
      (r.amount / 100).toFixed(2),
      r.account,
      r.gift?.event,
      r.note,
    ]);
  }
  lines.push(['合计', '', '', '', '', '', (total / 100).toFixed(2)]);
  return (
    '\ufeff' +
    lines
      .map((cells) =>
        cells
          .map((value) => {
            let text = String(value ?? '');
            if (/^\s*[=+@-]|^[\t\r\n]/.test(text)) text = "'" + text;
            return '"' + text.replaceAll('"', '""') + '"';
          })
          .join(','),
      )
      .join('\r\n')
  );
}

// 文件名包含礼单与导出日期，便于离线核账。
export function exportGiftCsv(list, rows) {
  download(
    list.name + '-' + localDay(new Date()) + '.csv',
    giftCsv(list, rows),
    'text/csv;charset=utf-8',
  );
}
