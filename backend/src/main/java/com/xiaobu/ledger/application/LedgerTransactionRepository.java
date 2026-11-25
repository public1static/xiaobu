package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerTransaction;
import java.time.YearMonth;
import java.util.List;

public interface LedgerTransactionRepository {

  void add(String ledgerId, String actorId, LedgerTransaction transaction);

  List<LedgerTransaction> findByLedgerAndMonth(String ledgerId, YearMonth month);
}
