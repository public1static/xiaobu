import { asDate, localDay, save, validDay } from './ledger.mjs';
import { validCategoryLabel } from './CategoryCatalog.mjs';

// 周期以首次付款月为锚点，跨年时也按相同月数间隔计算。
export const cycleMonths = Object.freeze({ month: 1, quarter: 3, halfYear: 6, year: 12 });
export const cycleLabels = Object.freeze({
  month: '月付',
  quarter: '季付',
  halfYear: '半年付',
  year: '年付',
});
// 各期按首次约定的日号计算，月末和闰日只缩短当期。
export function dueDate(bill, month) {
  if (
    !validDay(month + '-01') ||
    month < bill.start.slice(0, 7) ||
    (bill.stopMonth && month >= bill.stopMonth)
  )
    return '';
  const elapsed =
    (Number(month.slice(0, 4)) - Number(bill.start.slice(0, 4))) * 12 +
    Number(month.slice(5)) -
    Number(bill.start.slice(5, 7));
  if (!Object.hasOwn(cycleMonths, bill.cycle) || elapsed % cycleMonths[bill.cycle] !== 0) return '';
  const first = asDate(month + '-01');
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  return month + '-' + String(Math.min(last, Number(bill.start.slice(-2)))).padStart(2, '0');
}
// 合同只列租期内的账期，保留在旧计划中已经结清的其他月份。
export function leaseDue(bill, lease, month) {
  const due = dueDate(bill, month);
  return due && (!lease || (due >= lease.start && due <= lease.end) || bill.paid[month]) ? due : '';
}

// 模板允许浮动金额，关联表记录期月份及真实流水编号。
export function validFixedBill(b) {
  return (
    b &&
    typeof b.id === 'string' &&
    b.id &&
    typeof b.name === 'string' &&
    b.name.trim() &&
    b.name.length <= 40 &&
    validDay(b.start) &&
    (!b.stopMonth || validDay(b.stopMonth + '-01')) &&
    Object.hasOwn(cycleMonths, b.cycle) &&
    validCategoryLabel(b.category) &&
    typeof b.account === 'string' &&
    (b.amount === null || (Number.isSafeInteger(b.amount) && b.amount > 0)) &&
    b.paid &&
    typeof b.paid === 'object' &&
    !Array.isArray(b.paid) &&
    Object.entries(b.paid).every(([m, id]) => dueDate(b, m) && typeof id === 'string' && id)
  );
}
// 活跃流水是付款凭据；移到最近删除后该期重新显示为待付。
export function billPeriods(book, month) {
  const rows = new Map(book.rows.map((r) => [r.id, r]));
  const leases = new Map((book.leases || []).map((l) => [l.billId, l]));
  return (book.fixedBills || [])
    .map((bill) => {
      const row = rows.get(bill.paid[month]);
      return {
        bill,
        due: leaseDue(bill, leases.get(bill.id), month),
        row: row?.kind === 'expense' ? row : null,
      };
    })
    .filter((p) => p.due)
    .sort((a, b) => a.due.localeCompare(b.due) || a.bill.name.localeCompare(b.bill.name, 'zh-CN'));
}
// 每次只提交一个账本快照，关联旧流水时不改金额和账户余额。
export function settleBill(
  book,
  id,
  month,
  { rowId, payment },
  makeId = () => crypto.randomUUID(),
) {
  const period = billPeriods(book, month).find((p) => p.bill.id === id);
  if (!period || period.row) return { error: '本期已付款或账期不存在。' };
  const bill = period.bill;
  let next = book;
  if (rowId) {
    const existing = book.rows.find(
      (r) => r.id === rowId && r.kind === 'expense' && r.scope === 'personal',
    );
    if (!existing || book.fixedBills.some((b) => Object.values(b.paid).includes(rowId)))
      return { error: '这笔流水已关联或不可用。' };
  } else {
    rowId = makeId();
    next = save(
      book,
      { ...payment, scope: 'personal', kind: 'expense', category: bill.category, note: bill.name },
      rowId,
    );
    if (!next) return { error: '请检查实际金额和付款日期。' };
  }
  return {
    book: {
      ...next,
      fixedBills: next.fixedBills.map((b) =>
        b.id === id ? { ...b, paid: { ...b.paid, [month]: rowId } } : b,
      ),
    },
  };
}
// 月份导航在首日移动，避免月末溢出。
export function shiftMonth(month, step) {
  const d = asDate(month + '-01');
  d.setMonth(d.getMonth() + step);
  return localDay(d).slice(0, 7);
}

// 保留当前月份和预付账期，按原计划的周期找到下一次可调整的到期日。
export function nextPlanDate(bill, today) {
  const last = [today.slice(0, 7), ...Object.keys(bill.paid)].sort().at(-1);
  let month = bill.start.slice(0, 7);
  if (month <= last) {
    month = shiftMonth(last, 1);
    while (!dueDate({ ...bill, stopMonth: undefined }, month)) month = shiftMonth(month, 1);
  }
  return dueDate({ ...bill, stopMonth: undefined }, month);
}
// 将后续计划与历史账期分开保存，一次提交截止月份和替代计划。
export function revisePlan(book, id, today, replacement) {
  const bill = book.fixedBills?.find((b) => b.id === id);
  if (!bill || bill.stopMonth || book.leases?.some((l) => l.billId === id)) return null;
  const start = nextPlanDate(bill, today),
    stopMonth = start.slice(0, 7);
  if (replacement && (replacement.start < start || !validFixedBill(replacement))) return null;
  const plans = book.fixedBills.map((b) => (b.id === id ? { ...b, stopMonth } : b));
  if (replacement) plans.push(replacement);
  return { ...book, fixedBills: plans };
}

// 通过付款映射识别所属账期，编辑旧流水时也能还原关联。
export function paymentPeriod(book, rowId) {
  if (!rowId) return null;
  for (const bill of book.fixedBills || []) {
    const match = Object.entries(bill.paid).find(([, id]) => id === rowId);
    if (match)
      return { bill, month: match[0], lease: book.leases?.find((l) => l.billId === bill.id) };
  }
  return null;
}

// 从合同和付款周期列出实际到期项。删除的付款凭据不占用账期。
export function rentChoices(book, rowId) {
  const choices = [],
    activeRows = new Set(book.rows.filter((r) => r.kind === 'expense').map((r) => r.id));
  for (const lease of book.leases || []) {
    const bill = book.fixedBills?.find((b) => b.id === lease.billId);
    if (!bill) continue;
    const home = book.rentalHomes?.find((h) => h.id === lease.homeId)?.name || bill.name;
    // 缩短合同后仍可编辑先前结清的账期。
    const prior = rowId && Object.entries(bill.paid).find(([, id]) => id === rowId);
    if (prior) {
      const due = dueDate(bill, prior[0]);
      if (due && (due < lease.start || due > lease.end))
        choices.push({ billId: bill.id, month: prior[0], due, home, amount: bill.amount });
    }
    const start = [lease.start, bill.start].sort().at(-1).slice(0, 7);
    for (let month = start; month <= lease.end.slice(0, 7);) {
      const due = dueDate(bill, month),
        paid = bill.paid[month];
      if (
        due &&
        due >= lease.start &&
        due <= lease.end &&
        (!activeRows.has(paid) || paid === rowId)
      ) {
        choices.push({ billId: bill.id, month, due, home, amount: bill.amount });
      }
      if (month === lease.end.slice(0, 7)) break;
      month = shiftMonth(month, 1);
    }
  }
  return choices;
}

// 直接关联已保存的同一笔支出，改选账期时同时撤回原账期的付款标记。
export function setRentPeriod(book, rowId, target) {
  const previous = paymentPeriod(book, rowId);
  if (previous && !previous.lease) return { error: '这笔流水已关联其他固定账单。' };
  if (target && !book.leases?.some((l) => l.billId === target.billId))
    return { error: '租房账单已变更，请重新选择。' };
  if (target && previous?.bill.id === target.billId && previous.month === target.month)
    return { book };
  let next = book;
  if (previous) {
    const paid = { ...previous.bill.paid };
    delete paid[previous.month];
    next = {
      ...book,
      fixedBills: book.fixedBills.map((b) => (b.id === previous.bill.id ? { ...b, paid } : b)),
    };
  }
  return target ? settleBill(next, target.billId, target.month, { rowId }) : { book: next };
}
