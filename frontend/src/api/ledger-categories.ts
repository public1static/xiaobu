import type { TransactionCapability } from './transaction-extensions';
import { request } from './request';
import type { TransactionDirection } from '../pages/ledger/transaction';

export interface LedgerCategory {
  code: string;
  direction: TransactionDirection;
  name: string;
  active: boolean;
  capabilities?: TransactionCapability[];
}

const path = (ledgerId: string) => '/ledgers/' + encodeURIComponent(ledgerId) + '/categories';

export function listLedgerCategories(ledgerId: string) {
  return request<LedgerCategory[]>(path(ledgerId), 'GET');
}

export function createLedgerCategory(
  ledgerId: string,
  direction: TransactionDirection,
  name: string,
) {
  return request<LedgerCategory>(path(ledgerId), 'POST', { direction, name });
}

export function changeLedgerCategoryOrder(
  ledgerId: string,
  direction: TransactionDirection,
  codes: string[],
) {
  return request<LedgerCategory[]>(path(ledgerId) + '/order', 'PUT', { direction, codes });
}

export function changeLedgerCategoryStatus(ledgerId: string, code: string, active: boolean) {
  return request<LedgerCategory[]>(
    path(ledgerId) + '/' + encodeURIComponent(code) + '/status',
    'PUT',
    { active },
  );
}
