package com.xiaobu.ledger.infrastructure;

interface LedgerAccountBalanceRow {

  String getAccountId();

  long getBalanceCents();
}
