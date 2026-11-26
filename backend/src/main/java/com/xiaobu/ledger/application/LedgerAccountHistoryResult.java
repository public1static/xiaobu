package com.xiaobu.ledger.application;

public interface LedgerAccountHistoryResult {
  // 资金变动编号。
  String getId();

  // 资金变动来源。
  String getKind();

  // 业务日期。
  String getBookedOn();

  // 金额（分）。
  long getAmountCents();

  // 流水分类。
  String getCategoryCode();

  // 原单编号。
  String getTransactionId();

  // 转出账户。
  String getSourceAccountId();

  // 转入账户。
  String getTargetAccountId();

  // 资金变动备注。
  String getNote();

  // 是否本人创建。
  int getOwnRecord();

  // 关联的业务名称，例如押金所属的房屋。
  String getRelatedName();
}
