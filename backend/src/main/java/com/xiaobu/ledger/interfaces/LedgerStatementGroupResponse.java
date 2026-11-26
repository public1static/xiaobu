package com.xiaobu.ledger.interfaces;

/**
 * 当前筛选下一组流水的金额构成；未选账户时分组键为空。
 *
 * @param groupKey 当前分组的维度值，未选择账户时为空
 */
record LedgerStatementGroupResponse(
    String groupKey,
    long entryCount,
    long incomeCents,
    long grossExpenseCents,
    long refundCents,
    long netExpenseCents) {}
