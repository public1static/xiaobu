package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerTransaction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LedgerTransactionRepository {

  void add(String ledgerId, String actorId, LedgerTransaction transaction);

  List<LedgerTransaction> findByLedgerAndDateRange(
      String ledgerId, LocalDate from, LocalDate through, String keyword);

  Optional<LedgerTransaction> findActiveByLedgerAndId(String ledgerId, String transactionId);
}
