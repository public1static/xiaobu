import { request } from './request';

export type LedgerMemberRole = 'READ_ONLY' | 'CAN_RECORD';

export interface JoinedLedger {
  ledgerId: string;
  inviterName: string;
  role: LedgerMemberRole;
}

interface LedgerInvitation {
  token: string;
  expiresAt: string;
}

export interface LedgerInvitationPreview {
  inviterName: string;
  expiresAt: string;
  accepted: boolean;
}

export function createLedgerInvitation(ledgerId: string) {
  return request<LedgerInvitation>(`/ledgers/${encodeURIComponent(ledgerId)}/invitations`, 'POST');
}

export function previewLedgerInvitation(token: string) {
  return request<LedgerInvitationPreview>('/ledger-invitations/preview', 'POST', { token });
}

export function acceptLedgerInvitation(token: string) {
  return request<JoinedLedger>('/ledger-invitations/accept', 'POST', { token });
}

export function listJoinedLedgers() {
  return request<JoinedLedger[]>('/ledgers/joined', 'GET');
}
