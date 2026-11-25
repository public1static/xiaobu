import type { LedgerMemberRole } from './ledger-invitations';
import { request } from './request';

export interface LedgerMember {
  memberId: string;
  displayName: string;
  role: LedgerMemberRole;
}

function memberPath(ledgerId: string) {
  return `/ledgers/${encodeURIComponent(ledgerId)}/members`;
}

export function listLedgerMembers(ledgerId: string) {
  return request<LedgerMember[]>(memberPath(ledgerId), 'GET');
}

export function changeLedgerMemberRole(ledgerId: string, memberId: string, role: LedgerMemberRole) {
  return request<void>(`${memberPath(ledgerId)}/${encodeURIComponent(memberId)}/role`, 'PUT', {
    role,
  });
}

export function removeLedgerMember(ledgerId: string, memberId: string) {
  return request<void>(`${memberPath(ledgerId)}/${encodeURIComponent(memberId)}`, 'DELETE');
}
