import { request } from './request';

export interface LedgerAccount {
  id: string;
  name: string;
  recordedBalanceCents: number;
  openingBalanceCents: number;
  balanceCents: number;
}

function accountsPath(ledgerId: string) {
  return `/ledgers/${encodeURIComponent(ledgerId)}/accounts`;
}

export function listLedgerAccounts(ledgerId: string) {
  return request<LedgerAccount[]>(accountsPath(ledgerId), 'GET');
}

export function createLedgerAccount(ledgerId: string, name: string, openingBalanceCents: number) {
  return request<LedgerAccount>(accountsPath(ledgerId), 'POST', { name, openingBalanceCents });
}

export interface AccountTransferDraft {
  sourceAccountId: string;
  targetAccountId: string;
  amountCents: number;
  bookedOn: string;
  note: string;
}

export interface AccountTransfer extends AccountTransferDraft {
  id: string;
}

function transfersPath(ledgerId: string) {
  return `/ledgers/${encodeURIComponent(ledgerId)}/account-transfers`;
}

export function listAccountTransfers(ledgerId: string) {
  return request<AccountTransfer[]>(transfersPath(ledgerId), 'GET');
}

export function recordAccountTransfer(ledgerId: string, draft: AccountTransferDraft) {
  return request<AccountTransfer>(transfersPath(ledgerId), 'POST', draft);
}
