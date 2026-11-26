import type { GiftBook } from '../../api/ledger-gifts';
import type { ExtensionEditor, TransactionEntryPreset } from './transaction-extension-ui';

export const GIFT_TYPE = 'GIFT';

export function giftEntryPreset(book: GiftBook): TransactionEntryPreset {
  return {
    type: GIFT_TYPE,
    direction: book.direction,
    title: '登记礼金',
    detail: {
      bookId: book.daily ? null : book.id,
      bookName: book.name,
      reason: book.defaultReason ?? '',
    },
    defaultDate: book.defaultDate,
    unavailableMessage: '请先恢复该方向的礼金分类',
    continuedMessage: '已登记，可记下一位',
  };
}

export const giftExtensionEditor: ExtensionEditor = {
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
  validate: (detail, context) => {
    if (context.fromPreset && !String(detail.personName ?? '').trim()) {
      return '快速登记请填写送礼人或随礼对象。';
    }
    if (detail.needsContactLookup) return '请先重试加载亲友，再登记姓名。';
    if (detail.ambiguous) return '有多位同名亲友，请选一位或新增同名。';
    if (detail.sameName && !String(detail.distinction ?? '').trim())
      return '请填写称呼或关系，区分同名亲友。';
    return '';
  },
  errorMessage: (code) =>
    code === 'XIAOBU_GIFT_DIRECTORY_CONFLICT'
      ? '同名亲友的称呼或关系已被使用，请修改后再保存。'
      : code === 'XIAOBU_TRANSACTION_EXTENSION_LOCKED'
        ? '礼单已归档，重新开放后再修改。'
        : undefined,
};
