import type { CategoryCode, TransactionDirection } from '../pages/ledger/transaction';
import { request } from './request';

export interface LedgerTransactionDraft {
  direction: TransactionDirection;
  amountCents: number;
  bookedOn: string;
  categoryCode: CategoryCode;
  note: string;
  accountId?: string | null;
}

export type LedgerTransaction = LedgerTransactionDraft & { id: string };

function transactionPath(ledgerId: string) {
  return `/ledgers/${encodeURIComponent(ledgerId)}/transactions`;
}

export interface LedgerTransactionFilter {
  from: string;
  through: string;
  keyword: string;
}

export interface LedgerTransactionDetail {
  transaction: LedgerTransaction;
  authorName: string;
  accountName: string | null;
}

export function listLedgerTransactions(
  ledgerId: string,
  month: string,
  filter: LedgerTransactionFilter,
) {
  const dates =
    filter.from && filter.through
      ? `from=${encodeURIComponent(filter.from)}&through=${encodeURIComponent(filter.through)}`
      : `month=${encodeURIComponent(month)}`;
  const query = filter.keyword ? `${dates}&keyword=${encodeURIComponent(filter.keyword)}` : dates;
  return request<LedgerTransaction[]>(`${transactionPath(ledgerId)}?${query}`, 'GET');
}

export function getLedgerTransaction(ledgerId: string, transactionId: string) {
  return request<LedgerTransactionDetail>(
    `${transactionPath(ledgerId)}/${encodeURIComponent(transactionId)}`,
    'GET',
  );
}

export function recordLedgerTransaction(ledgerId: string, draft: LedgerTransactionDraft) {
  return request<LedgerTransaction>(transactionPath(ledgerId), 'POST', draft);
}
