import type { ManualReminderFrequency } from '../pages/ledger/manual-reminder-schedule';
import { request } from './request';

export interface LedgerManualReminder {
  id: string;
  title: string;
  dueOn: string;
  remindAt: string;
  leadDays: number;
  completed: boolean;
  frequency: ManualReminderFrequency;
  anchorOn: string;
  revision: number;
  stopped: boolean;
  canUndo: boolean;
}

export interface LedgerManualReminderInput {
  title: string;
  dueOn: string;
  time: string;
  frequency: ManualReminderFrequency;
  revision?: number;
  leadDays: number;
}

const path = (ledgerId: string, id?: string) =>
  '/ledgers/' +
  encodeURIComponent(ledgerId) +
  '/manual-reminders' +
  (id ? '/' + encodeURIComponent(id) : '');

export function getLedgerManualReminder(ledgerId: string, id: string, revision?: number) {
  return request<LedgerManualReminder>(path(ledgerId, id), 'GET', { revision });
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
  revision: number,
  completed: boolean,
) {
  return request<LedgerManualReminder>(path(ledgerId, id) + '/completion', 'PUT', {
    revision,
    completed,
  });
}

export function stopLedgerManualReminder(ledgerId: string, id: string, revision: number) {
  return request<LedgerManualReminder>(path(ledgerId, id) + '/stop', 'PUT', { revision });
}
