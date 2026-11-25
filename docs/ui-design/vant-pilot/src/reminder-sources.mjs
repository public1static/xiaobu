import { manualReminder } from './manual-reminders.mjs';
import { amountText } from './ledger.mjs';
import { dueDate, shiftMonth, cycleMonths } from './fixed-bills.mjs';

// 每个未付账期提供一个待办；房租沿用同一固定账单及付款记录。
export const billReminder = {
  kind: 'bill',
  defaultDays: '3',
  days: [0, 1, 3, 7],
  *pending(book, today) {
    const paid = new Set(book.rows.filter((r) => r.kind === 'expense').map((r) => r.id));
    const bindings = new Map((book.leases || []).map((l) => [l.billId, l]));
    for (const bill of book.fixedBills || []) {
      const lease = bindings.get(bill.id);
      // 保留所有欠付账期，找到首个未来未付账期后停止展开。
      if (!cycleMonths[bill.cycle]) continue;
      for (
        let month = bill.start.slice(0, 7);
        month.length === 7 &&
        (!bill.stopMonth || month < bill.stopMonth) &&
        (!lease || month <= lease.end.slice(0, 7));
        month = shiftMonth(month, cycleMonths[bill.cycle])
      ) {
        const due = dueDate(bill, month);
        if (!due || (lease && (due < lease.start || due > lease.end)) || paid.has(bill.paid[month]))
          continue;
        yield {
          id: bill.id,
          occurrence: month,
          due,
          remindDays: bill.remindDays,
          title: bill.name,
          subtitle: month + ' 账期 · 待付',
          value: bill.amount == null ? '金额待定' : '¥' + amountText(bill.amount),
          target: { workspace: 'fixed', props: { openBill: bill.id, initialMonth: month } },
        };
        if (due > today) break;
      }
    }
  },
};

// 单次到期以合同结束日期标识；续期后有独立的办理状态。
export const leaseReminder = {
  kind: 'lease',
  defaultDays: '30',
  days: [7, 15, 30],
  *pending(book) {
    for (const lease of book.leases || []) {
      if (lease.handledEnd === lease.end) continue;
      const home = book.rentalHomes?.find((h) => h.id === lease.homeId);
      yield {
        id: lease.id,
        occurrence: lease.end,
        due: lease.end,
        remindDays: lease.remindDays,
        title: home?.name || '租房合同',
        subtitle: ['合同到期', lease.party].filter(Boolean).join(' · '),
        target: { workspace: 'rentals', props: { initialLease: lease.id } },
      };
    }
  },
};

export const reminderSources = [billReminder, leaseReminder, manualReminder];
