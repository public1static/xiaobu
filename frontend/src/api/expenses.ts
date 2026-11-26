import type { CategoryCode } from '../pages/ledger/expense';
import { request } from './request';

export interface ExpenseDraft {
  amountCents: number;
  bookedOn: string;
  categoryCode: CategoryCode;
  note: string;
}

export type Expense = ExpenseDraft & { id: string };

function expensePath(ledgerId: string) {
  return `/ledgers/${encodeURIComponent(ledgerId)}/expenses`;
}

export function listExpenses(ledgerId: string, month: string) {
  return request<Expense[]>(`${expensePath(ledgerId)}?month=${encodeURIComponent(month)}`, 'GET');
}

export function saveExpense(ledgerId: string, draft: ExpenseDraft) {
  return request<Expense>(expensePath(ledgerId), 'POST', draft);
}
