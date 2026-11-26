import { GIFT_TYPE, giftExtensionEditor } from './gift-entry-extension';
import type { TransactionDirection } from './transaction';
import type { TransactionExtensionDetail } from '../../api/transaction-extensions';

export interface TransactionEntryPreset {
  type: string;
  direction: TransactionDirection;
  title: string;
  detail: TransactionExtensionDetail;
  defaultDate: string | null;
  unavailableMessage: string;
  continuedMessage: string;
}

export interface ExtensionValidationContext {
  fromPreset: boolean;
}

export interface ExtensionEditor {
  toDraft(detail: TransactionExtensionDetail): TransactionExtensionDetail;
  toWire(detail: TransactionExtensionDetail): TransactionExtensionDetail;
  validate(detail: TransactionExtensionDetail, context: ExtensionValidationContext): string;
  errorMessage?(code: string): string | undefined;
}

const modules = new Map<string, ExtensionEditor>([[GIFT_TYPE, giftExtensionEditor]]);

export const supportsTransactionExtension = (type: string) => modules.has(type);
export const extensionDraft = (type: string, detail: TransactionExtensionDetail = {}) =>
  modules.get(type)?.toDraft(detail) ?? {};
export const extensionPayload = (type: string, detail: TransactionExtensionDetail = {}) =>
  modules.get(type)?.toWire(detail) ?? {};
export const validateExtensionDraft = (
  type: string,
  detail: TransactionExtensionDetail = {},
  context: ExtensionValidationContext,
) => modules.get(type)?.validate(detail, context) ?? '';

export function extensionErrorMessage(types: readonly string[], errorCode: string) {
  return types.map((type) => modules.get(type)?.errorMessage?.(errorCode)).find(Boolean);
}
