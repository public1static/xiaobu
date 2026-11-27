import { request } from './request';

export interface LedgerManualReminder {
  id: string;
  title: string;
  dueOn: string;
  remindAt: string;
  leadDays: number;
  completed: boolean;
}

export interface LedgerManualReminderInput {
  title: string;
  dueOn: string;
  time: string;
  leadDays: number;
}

const path = (ledgerId: string, id?: string) =>
  '/ledgers/' +
  encodeURIComponent(ledgerId) +
  '/manual-reminders' +
  (id ? '/' + encodeURIComponent(id) : '');

export function getLedgerManualReminder(ledgerId: string, id: string) {
  return request<LedgerManualReminder>(path(ledgerId, id), 'GET');
}

export function saveLedgerManualReminder(
  ledgerId: string,
  input: LedgerManualReminderInput,
  id?: string,
) {
  return request<LedgerManualReminder>(path(ledgerId, id), id ? 'PUT' : 'POST', input);
}

export function setLedgerManualReminderCompletion(
  ledgerId: string,
  id: string,
  completed: boolean,
) {
  return request<LedgerManualReminder>(path(ledgerId, id) + '/completion', 'PUT', { completed });
}
