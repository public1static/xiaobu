import { request } from './request';

export interface LedgerTodo {
  source: string;
  sourceId: string;
  occurrence: string;
  dueOn: string;
  status: 'PENDING' | 'PLANNED' | 'COMPLETED';
  title: string;
  subtitle: string;
  amountCents: number | null;
}

interface LedgerTodoPage {
  entries: LedgerTodo[];
  totalCount: number;
  pendingCount: number;
  page: number;
  pageSize: number;
  today: string;
}

const path = (ledgerId: string) => '/ledgers/' + encodeURIComponent(ledgerId) + '/todos';

export function listLedgerTodos(ledgerId: string, completed: boolean, page = 0) {
  return request<LedgerTodoPage>(path(ledgerId), 'GET', { completed, page });
}

export function countPendingLedgerTodos(ledgerId: string) {
  return request<{ pendingCount: number }>(path(ledgerId) + '/count', 'GET');
}
