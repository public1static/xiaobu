import { request } from './request';

export interface LedgerMonthlyBudget {
  month: string;
  amountCents: number | null;
  usedCents: number;
  remainingCents: number | null;
}

function monthlyBudgetPath(ledgerId: string, month: string) {
  return (
    '/ledgers/' + encodeURIComponent(ledgerId) + '/budgets/monthly/' + encodeURIComponent(month)
  );
}

export function getLedgerMonthlyBudget(ledgerId: string, month: string) {
  return request<LedgerMonthlyBudget>(monthlyBudgetPath(ledgerId, month), 'GET');
}

export function setLedgerMonthlyBudget(ledgerId: string, month: string, amountCents: number) {
  return request<LedgerMonthlyBudget>(monthlyBudgetPath(ledgerId, month), 'PUT', { amountCents });
}
