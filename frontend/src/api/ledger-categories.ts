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

// 内置兜底分类在两个方向统一显示“其它”，保留自定义分类的原名。
function displayNames(rows: LedgerCategory[]) {
  return rows.map((row) =>
    row.code === 'OTHER' || row.code === 'OTHER_INCOME' ? { ...row, name: '其它' } : row,
  );
}
export function listLedgerCategories(ledgerId: string) {
  return request<LedgerCategory[]>(path(ledgerId), 'GET').then(displayNames);
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
  return request<LedgerCategory[]>(path(ledgerId) + '/order', 'PUT', { direction, codes }).then(
    displayNames,
  );
}

export function changeLedgerCategoryStatus(ledgerId: string, code: string, active: boolean) {
  return request<LedgerCategory[]>(
    path(ledgerId) + '/' + encodeURIComponent(code) + '/status',
    'PUT',
    { active },
  ).then(displayNames);
}
