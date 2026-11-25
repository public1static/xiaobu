import type { CategoryCode, TransactionDirection } from '../pages/ledger/transaction';
import type { TransactionExtension, TransactionExtensionChange } from './transaction-extensions';
import { request } from './request';

export interface LedgerTransactionDraft {
  direction: TransactionDirection;
  amountCents: number;
  bookedOn: string;
  categoryCode: CategoryCode;
  note: string;
  accountId?: string | null;
  extensions?: TransactionExtensionChange[];
}

export type LedgerTransaction = LedgerTransactionDraft & {
  id: string;
  originalTransactionId?: string | null;
};

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
  version: number;
  canModify: boolean;
  accountName: string | null;
  refunds: LedgerRefund[];
  extensions?: TransactionExtension[];
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

export interface DeletedLedgerTransaction {
  transaction: LedgerTransaction;
  version: number;
}

export function listRecentlyDeleted(ledgerId: string) {
  return request<DeletedLedgerTransaction[]>(
    `${transactionPath(ledgerId)}/recently-deleted`,
    'GET',
  );
}

export function editLedgerTransaction(
  ledgerId: string,
  transactionId: string,
  version: number,
  draft: LedgerTransactionDraft,
) {
  return request<void>(`${transactionPath(ledgerId)}/${encodeURIComponent(transactionId)}`, 'PUT', {
    version,
    draft,
  });
}

export function deleteLedgerTransaction(ledgerId: string, transactionId: string, version: number) {
  return request<void>(
    `${transactionPath(ledgerId)}/${encodeURIComponent(transactionId)}?version=${version}`,
    'DELETE',
  );
}

export function restoreLedgerTransaction(ledgerId: string, transactionId: string, version: number) {
  return request<void>(
    `${transactionPath(ledgerId)}/${encodeURIComponent(transactionId)}/restore`,
    'POST',
    { version },
  );
}

export interface LedgerRefund {
  id: string;
  amountCents: number;
  receivedOn: string;
  accountId: string | null;
  accountName: string | null;
}

export function recordLedgerRefund(
  ledgerId: string,
  transactionId: string,
  draft: { version: number; amountCents: number; receivedOn: string; accountId: string | null },
) {
  return request<LedgerRefund>(
    `${transactionPath(ledgerId)}/${encodeURIComponent(transactionId)}/refunds`,
    'POST',
    draft,
  );
}
