import type { TransactionExtensionDetail } from '../../api/transaction-extensions';

interface ExtensionEditor {
  toDraft(detail: TransactionExtensionDetail): TransactionExtensionDetail;
  toWire(detail: TransactionExtensionDetail): TransactionExtensionDetail;
  validate(detail: TransactionExtensionDetail): string;
}

const modules = new Map<string, ExtensionEditor>([
  [
    'GIFT',
    {
      toDraft: (detail) => ({
        ...detail,
        personName: String(detail.personName ?? ''),
        reason: String(detail.reason ?? ''),
      }),
      toWire: (detail) => ({
        personName: detail.personName ?? '',
        reason: detail.reason ?? '',
        bookId: detail.bookId ?? null,
        contactId: detail.contactId ?? null,
        createContact: Boolean(detail.createContact),
        distinction: detail.distinction ?? '',
      }),
      validate: (detail) => {
        if (detail.needsContactLookup) return '请先重试加载亲友，再登记姓名。';
        if (detail.ambiguous) return '有多位同名亲友，请选一位或新增同名。';
        if (detail.sameName && !String(detail.distinction ?? '').trim())
          return '请填写称呼或关系，区分同名亲友。';
        return '';
      },
    },
  ],
]);

export const supportsTransactionExtension = (type: string) => modules.has(type);
export const extensionDraft = (type: string, detail: TransactionExtensionDetail = {}) =>
  modules.get(type)?.toDraft(detail) ?? {};
export const extensionPayload = (type: string, detail: TransactionExtensionDetail = {}) =>
  modules.get(type)?.toWire(detail) ?? {};
export const validateExtensionDraft = (type: string, detail: TransactionExtensionDetail = {}) =>
  modules.get(type)?.validate(detail) ?? '';
