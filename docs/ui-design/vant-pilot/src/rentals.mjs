import { cycleMonths, dueDate, shiftMonth } from './fixed-bills.mjs';
import { validDay } from './ledger.mjs';

// 金额使用整数分，空白的约定押金视为零。
export function rentCents(value) {
  const text = String(value ?? '').trim();
  return /^\d{1,8}(?:\.\d{1,2})?$/.test(text) ? Math.round(Number(text) * 100) : NaN;
}
export function validLease(l) {
  return (
    l &&
    Object.hasOwn(cycleMonths, l.cycle ?? 'month') &&
    l.id &&
    l.homeId &&
    typeof l.party === 'string' &&
    l.party.trim() &&
    validDay(l.start) &&
    validDay(l.end) &&
    l.end >= l.start &&
    Number.isSafeInteger(l.rent) &&
    l.rent > 0 &&
    Number.isSafeInteger(l.deposit) &&
    l.deposit >= 0
  );
}
// 在同一次写入中保存合同与房租计划；编辑已绑定合同始终复用原计划。
export function storeLease(
  book,
  lease,
  mode,
  billId,
  firstDue,
  makeId = () => crypto.randomUUID(),
) {
  if (!validLease(lease) || !(book.rentalHomes || []).some((h) => h.id === lease.homeId))
    return { error: '请检查房屋、出租方、起止日期、月租和约定押金。' };
  const leases = book.leases || [],
    plans = book.fixedBills || [];
  if (
    leases.some(
      (l) =>
        l.id !== lease.id &&
        l.homeId === lease.homeId &&
        l.start <= lease.end &&
        l.end >= lease.start,
    )
  )
    return { field: 'end', error: '这套房屋在该时段已有合同，请检查租期。' };
  const cycle = lease.cycle ?? 'month',
    amount = lease.rent * cycleMonths[cycle];
  const prior = leases.find((l) => l.id === lease.id);
  let id = prior?.billId || '',
    nextPlans = plans;
  if (id || mode === 'link') {
    id = id || billId;
    const plan = plans.find((b) => b.id === id);
    if (
      !plan ||
      plan.stopMonth ||
      plan.cycle !== cycle ||
      leases.some((l) => l.id !== lease.id && l.billId === id)
    )
      return { field: 'planId', error: '请选择付款周期相同且尚未关联合同的账单。' };
    const firstMonth = plan.start > lease.start ? plan.start : lease.start;
    let month = firstMonth.slice(0, 7);
    let due = dueDate(plan, month);
    while (month <= lease.end.slice(0, 7) && (!due || due < lease.start)) {
      month = shiftMonth(month, 1);
      due = dueDate(plan, month);
    }
    if (!due || due > lease.end)
      return { field: 'planId', error: '账单与租期没有重叠，请核对日期。' };
    nextPlans = plans.map((b) => (b.id === id ? { ...b, amount } : b));
  } else if (mode === 'create') {
    if (!validDay(firstDue) || firstDue < lease.start || firstDue > lease.end)
      return { error: '首次付款日需在合同租期内。' };
    id = makeId();
    const home = book.rentalHomes.find((h) => h.id === lease.homeId);
    nextPlans = [
      ...plans,
      {
        id,
        name: (home.name + '房租').slice(0, 40),
        category: '住房',
        start: firstDue,
        amount,
        cycle,
        account: '',
        paid: {},
      },
    ];
  }
  const saved = { ...lease, cycle, billId: id };
  return {
    book: {
      ...book,
      fixedBills: nextPlans,
      leases: [...leases.filter((l) => l.id !== saved.id), saved],
    },
  };
}
// 实付与实退分开登记，可分次退款；按日期检验，避免先退后付。
export function storeDeposit(book, entry) {
  const lease = book.leases?.find((l) => l.id === entry.leaseId);
  if (
    !lease ||
    !entry.id ||
    !['pay', 'refund'].includes(entry.kind) ||
    !Number.isSafeInteger(entry.amount) ||
    entry.amount <= 0 ||
    !validDay(entry.date) ||
    typeof entry.account !== 'string'
  )
    return { error: '请填写有效的押金金额和日期。' };
  const entries = [...(book.depositEntries || []).filter((d) => d.id !== entry.id), entry];
  let held = 0;
  for (const d of entries
    .filter((d) => d.leaseId === lease.id)
    .sort(
      (a, b) =>
        a.date.localeCompare(b.date) || (a.kind === 'pay' ? -1 : 1) - (b.kind === 'pay' ? -1 : 1),
    )) {
    held += d.kind === 'pay' ? d.amount : -d.amount;
    if (held < 0) return { error: '退回金额超过当日已交押金。' };
  }
  return { book: { ...book, depositEntries: entries } };
}
