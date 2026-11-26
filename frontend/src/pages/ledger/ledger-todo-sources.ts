import { setLedgerManualReminderCompletion } from '../../api/ledger-manual-reminders';
import type { LedgerTodo } from '../../api/ledger-todos';

interface LedgerTodoTargetContext {
  openManualReminder: (id: string) => void;
  openContract: (contractId: string) => void;
  openFixedBill: (billId: string, month: string) => void;
}

interface LedgerTodoSource {
  label: string;
  action: string;
  completedLabel: string;
  complete?: (ledgerId: string, item: LedgerTodo, done: boolean) => Promise<unknown>;
  open: (item: LedgerTodo, context: LedgerTodoTargetContext) => void;
}

// 每个来源定义自己的目的地，待办列表不依赖分类名或业务路由。
const sources = new Map<string, LedgerTodoSource>([
  [
    'MANUAL_REMINDER',
    {
      label: '自建提醒',
      action: '查看提醒',
      completedLabel: '自建提醒 · 已办结',
      complete: (ledgerId, item, done) =>
        setLedgerManualReminderCompletion(ledgerId, item.sourceId, done),
      open: (item, context) => context.openManualReminder(item.sourceId),
    },
  ],
  [
    'RENTAL_EXPIRY',
    {
      label: '合同到期',
      action: '查看合同',
      completedLabel: '到期已处理',
      open: (item, context) => context.openContract(item.sourceId),
    },
  ],
  [
    'FIXED_BILL',
    {
      label: '账单待付',
      action: '查看账单',
      completedLabel: '账单已付',
      open: (item, context) => context.openFixedBill(item.sourceId, item.occurrence),
    },
  ],
]);

export function ledgerTodoSource(source: string) {
  return sources.get(source);
}

export function openLedgerTodo(item: LedgerTodo, context: LedgerTodoTargetContext) {
  ledgerTodoSource(item.source)?.open(item, context);
}

export function ledgerTodoKey(item: LedgerTodo) {
  return JSON.stringify([item.source, item.sourceId, item.occurrence]);
}
