package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerTransaction;
import com.xiaobu.ledger.domain.LedgerTransactionDetails;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LedgerTransactionRepository {

  void add(String ledgerId, String actorId, LedgerTransaction transaction);

  List<LedgerTransaction> findByLedgerAndDateRange(
      String ledgerId, LocalDate from, LocalDate through, String keyword);

  Optional<LedgerTransaction> findActiveByLedgerAndId(String ledgerId, String transactionId);

  Optional<LedgerTransaction> findByLedgerAndId(String ledgerId, String transactionId);

  List<LedgerTransaction> findRecentlyDeleted(String ledgerId, String creatorFilter);

  void edit(
      String ledgerId, String transactionId, String actorId, LedgerTransactionDetails details);

  void changeDeletion(String ledgerId, String transactionId, String actorId, boolean deleted);
}
