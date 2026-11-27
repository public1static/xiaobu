import { request } from './request';
import type { GiftContact } from './ledger-gifts';

export interface LedgerContactMatches {
  entries: GiftContact[];
  totalCount: number;
  page: number;
  pageSize: number;
}

export function listLedgerContactMatches(ledgerId: string, name: string, page = 0) {
  return request<LedgerContactMatches>(
    '/ledgers/' + encodeURIComponent(ledgerId) + '/contacts/matches',
    'GET',
    { name, page },
  );
}
