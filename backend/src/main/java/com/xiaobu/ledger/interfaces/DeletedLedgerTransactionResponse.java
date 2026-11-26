package com.xiaobu.ledger.interfaces;

record DeletedLedgerTransactionResponse(LedgerTransactionResponse transaction, long version) {}
