export interface TransactionCapability {
  type: string;
  required: boolean;
}

export type TransactionExtensionDetail = Record<string, unknown>;

export interface TransactionExtension {
  type: string;
  detail: TransactionExtensionDetail;
}

export type TransactionExtensionChange =
  | { type: string; operation: 'UPSERT'; detail: TransactionExtensionDetail }
  | { type: string; operation: 'REMOVE' };
