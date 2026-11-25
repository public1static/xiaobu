import { request } from './request';

export interface LedgerAccount {
  id: string;
  name: string;
  recordedBalanceCents: number;
}

function accountsPath(ledgerId: string) {
  return `/ledgers/${encodeURIComponent(ledgerId)}/accounts`;
}

export function listLedgerAccounts(ledgerId: string) {
  return request<LedgerAccount[]>(accountsPath(ledgerId), 'GET');
}

export function createLedgerAccount(ledgerId: string, name: string) {
  return request<LedgerAccount>(accountsPath(ledgerId), 'POST', { name });
}
