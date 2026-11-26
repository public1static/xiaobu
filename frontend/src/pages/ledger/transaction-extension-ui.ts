import type { TransactionExtensionDetail } from '../../api/transaction-extensions';

const modules: Readonly<
  Record<
    string,
    {
      toDraft(detail: TransactionExtensionDetail): TransactionExtensionDetail;
    }
  >
> = {
  GIFT: {
    toDraft: (detail) => ({
      personName: String(detail.personName ?? ''),
      reason: String(detail.reason ?? ''),
    }),
  },
};

export function supportsTransactionExtension(type: string) {
  return Object.hasOwn(modules, type);
}

export function extensionDraft(type: string, detail: TransactionExtensionDetail = {}) {
  return modules[type]?.toDraft(detail) ?? {};
}
