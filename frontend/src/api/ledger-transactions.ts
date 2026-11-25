import type { CategoryCode, TransactionDirection } from '../pages/ledger/transaction';
import { request } from './request';

export interface LedgerTransactionDraft {
  direction: TransactionDirection;
  amountCents: number;
  bookedOn: string;
  categoryCode: CategoryCode;
  note: string;
}

export type LedgerTransaction = LedgerTransactionDraft & { id: string };

function transactionPath(ledgerId: string) {
  return `/ledgers/${encodeURIComponent(ledgerId)}/transactions`;
}

export function listLedgerTransactions(ledgerId: string, month: string) {
  return request<LedgerTransaction[]>(
    `${transactionPath(ledgerId)}?month=${encodeURIComponent(month)}`,
    'GET',
  );
}

export function recordLedgerTransaction(ledgerId: string, draft: LedgerTransactionDraft) {
  return request<LedgerTransaction>(transactionPath(ledgerId), 'POST', draft);
}
