import type { ExtensionEditor } from './transaction-extension-ui';

export const RENTAL_PAYMENT_TYPE = 'RENTAL_PAYMENT';

export const rentalPaymentExtensionEditor: ExtensionEditor = {
  toDraft: ({ billId, month, billName }) => ({ billId, month, billName }),
  toWire: ({ billId, month }) => ({ billId, month }),
  validate(detail) {
    if (!Object.keys(detail).length) return '';
    return typeof detail.billId === 'string' && typeof detail.month === 'string'
      ? ''
      : '请选择有效的房租账期。';
  },
  errorMessage(code) {
    if (code === 'XIAOBU_RENTAL_PAYMENT_CONFLICT') {
      return '房租账期已付、不可用，或这笔流水已关联其他账单。请重新选择。';
    }
    if (code === 'XIAOBU_RENTAL_PAYMENT_DATE') return '付款日期不能晚于今天。';
    return undefined;
  },
};
