import type { TransactionDirection } from '../pages/ledger/transaction';
import { request } from './request';

export interface GiftBook {
  id: string;
  name: string;
  direction: TransactionDirection;
  defaultDate: string | null;
  defaultReason: string | null;
  archived: boolean;
  daily?: boolean;
}

export interface GiftContact {
  id: string;
  name: string;
  distinction: string;
}

export interface GiftActivity {
  transactionId: string;
  bookId: string | null;
  contactId: string | null;
  personName: string | null;
  distinction: string | null;
  reason: string | null;
  note: string;
  bookedOn: string;
  amountCents: number;
  direction: TransactionDirection;
}

function path(ledgerId: string) {
  return `/ledgers/${encodeURIComponent(ledgerId)}/gifts`;
}

export function dailyGiftBook(direction: TransactionDirection): GiftBook {
  const receive = direction === 'INCOME';
  return {
    id: receive ? 'DAILY_RECEIVED' : 'DAILY_SENT',
    name: receive ? '日常收礼' : '日常送礼',
    direction,
    defaultDate: null,
    defaultReason: null,
    archived: false,
    daily: true,
  };
}

export function listGiftBooks(ledgerId: string) {
  return request<GiftBook[]>(path(ledgerId) + '/books', 'GET');
}

export function createGiftBook(
  ledgerId: string,
  draft: Omit<GiftBook, 'id' | 'archived' | 'daily'>,
) {
  return request<GiftBook>(path(ledgerId) + '/books', 'POST', draft);
}

export function setGiftBookArchived(ledgerId: string, id: string, archived: boolean) {
  return request<void>(path(ledgerId) + '/books/' + encodeURIComponent(id) + '/archive', 'PUT', {
    archived,
  });
}

export function listGiftContacts(ledgerId: string) {
  return request<GiftContact[]>(path(ledgerId) + '/contacts', 'GET');
}

export interface GiftActivityPage {
  entries: GiftActivity[];
  incomeCents: number;
  expenseCents: number;
  totalCount: number;
  page: number;
  pageSize: number;
}

export function listGiftActivity(
  ledgerId: string,
  filter: {
    bookId?: string;
    contactId?: string;
    keyword?: string;
    page?: number;
    pageSize?: number;
  } = {},
) {
  const parts = Object.entries(filter).map(([key, value]) => key + '=' + encodeURIComponent(value));
  return request<GiftActivityPage>(
    path(ledgerId) + '/activity' + (parts.length ? '?' + parts.join('&') : ''),
    'GET',
  );
}
