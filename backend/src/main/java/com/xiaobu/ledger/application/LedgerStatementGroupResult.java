package com.xiaobu.ledger.application;

/**
 * 当前筛选下一组流水的金额构成；未选账户时分组键为空。
 *
 * @param netExpenseCents 扣除到账退款后的支出金额，单位为分
 */
public record LedgerStatementGroupResult(
    String groupKey,
    long entryCount,
    long incomeCents,
    long grossExpenseCents,
    long refundCents,
    long netExpenseCents) {}
