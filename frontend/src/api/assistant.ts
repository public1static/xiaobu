import { request } from './request';
import type { LedgerStatementPage } from './ledger-transactions';

export interface Conversation {
  id: string;
  title: string;
  revision: number;
}
export interface Message {
  sequence: number;
  question: string;
  answer: {
    text: string;
    sourceId?: string | null;
    choices?: { label: string }[] | null;
    scope?: {
      plan: {
        from: string | null;
        through: string | null;
        direction?: 'INCOME' | 'EXPENSE' | null;
        category?: string | null;
        account?: string | null;
        recordedByMe: boolean;
        keyword?: string | null;
      };
    } | null;
    statement?: LedgerStatementPage | null;
  };
}

export interface DraftFields {
  bookedOn: string;
  amountCents: number | null;
  categoryCode: string | null;
  accountId: string | null;
  note: string;
}
export interface ExpenseDraft {
  id: string;
  version: number;
  status: 'PENDING' | 'CONFIRMED' | 'DISCARDED';
  fields: DraftFields;
  transactionId: string | null;
}

// 所有请求均携带当前账本，服务端同时核对会话归属。
export function assistantApi(ledger: string) {
  const path = '/ledgers/' + encodeURIComponent(ledger) + '/assistant/conversations';
  const messages = (id: string) => path + '/' + encodeURIComponent(id) + '/messages';
  const drafts = (id: string) => path + '/' + encodeURIComponent(id) + '/drafts';
  return {
    draft: (chat: string, id?: string) =>
      request<{ draft: ExpenseDraft | null }>(
        drafts(chat) + (id ? '?id=' + encodeURIComponent(id) : ''),
        'GET',
      ),
    editDraft: (c: Conversation, old: ExpenseDraft | null, question: string, fields: DraftFields) =>
      request<ExpenseDraft>(drafts(c.id), 'PUT', {
        revision: c.revision,
        id: old?.id,
        version: old?.version,
        question,
        fields,
      }),
    finishDraft: (chat: string, draft: ExpenseDraft, action: 'confirm' | 'discard') =>
      request<ExpenseDraft>(
        drafts(chat) + '/' + encodeURIComponent(draft.id) + '/' + action,
        'POST',
        { version: draft.version },
      ),
    list: (offset = 0) => request<Conversation[]>(path + '?offset=' + offset, 'GET'),
    create: () => request<Conversation>(path, 'POST'),
    history: (id: string, before?: number) =>
      request<Message[]>(messages(id) + (before ? '?before=' + before : ''), 'GET'),
    ask: (id: string, question: string, revision: number, clientId: string, replyTo?: number) =>
      request<Message>(messages(id), 'POST', { question, revision, clientId, replyTo }),
    entries: (id: string, seq: number, page: number) =>
      request<LedgerStatementPage>(messages(id) + '/' + seq + '/entries?page=' + page, 'GET'),
  };
}

// 重试时复用同一请求编号，用于从服务端找回已提交的回答。
export function chatRequestId() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const bits = Math.floor(Math.random() * 16);
    return (c === 'x' ? bits : (bits & 3) | 8).toString(16);
  });
}
