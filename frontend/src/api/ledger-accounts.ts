import { request } from './request';

export interface LedgerAccount {
  id: string;
  name: string;
  type: string;
  active: boolean;
  balanceKnown: boolean;
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

export function createLedgerAccount(
  ledgerId: string,
  name: string,
  openingBalanceCents: number,
  type?: string,
  balanceKnown = true,
) {
  return request<LedgerAccount>(accountsPath(ledgerId), 'POST', {
    name,
    openingBalanceCents,
    type,
    balanceKnown,
  });
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

// 账户详情的资金变动，退款关联原始支出。
export interface AccountHistoryEntry extends AccountTransfer {
  kind: 'INCOME' | 'EXPENSE' | 'REFUND' | 'TRANSFER';
  transactionId: string | null;
  categoryCode: string;
  ownRecord: number;
}
// 查询当前账户全部日期的流水。
export function listAccountHistory(ledgerId: string, id: string) {
  return request<AccountHistoryEntry[]>(
    accountsPath(ledgerId) + '/' + encodeURIComponent(id) + '/history',
    'GET',
  );
}
// 编辑时提交用户核对的当前余额，由服务端扣除历史变动。
export function updateLedgerAccount(
  ledgerId: string,
  id: string,
  draft: {
    name: string;
    type: string;
    active: boolean;
    preserveBalance: boolean;
    currentBalanceCents: number | null;
  },
) {
  return request<void>(accountsPath(ledgerId) + '/' + encodeURIComponent(id), 'PUT', draft);
}
// 保留原转账编号与记账人。
export function updateAccountTransfer(ledgerId: string, id: string, draft: AccountTransferDraft) {
  return request<void>(transfersPath(ledgerId) + '/' + encodeURIComponent(id), 'PUT', draft);
}
// 整笔撤销使两端余额同步恢复。
export function cancelAccountTransfer(ledgerId: string, id: string) {
  return request<void>(transfersPath(ledgerId) + '/' + encodeURIComponent(id), 'DELETE');
}
