import { request } from './request';

export type PreviewAccount = 'alpha' | 'beta';

export interface LocalSession {
  displayName: string;
  ledgerId: string;
}

export function getLocalSession() {
  return request<LocalSession>('/local-session', 'GET');
}

export function openLocalSession(account: PreviewAccount) {
  return request<LocalSession>('/local-session', 'POST', { account });
}

export function closeLocalSession() {
  return request<void>('/local-session', 'DELETE');
}
