package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerAccount;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LedgerAccountRepository {

  void add(LedgerAccount account);

  List<LedgerAccount> findByLedger(String ledgerId);

  Map<String, Long> findRecordedBalancesByLedger(String ledgerId);

  Optional<LedgerAccount> findByLedgerAndId(String ledgerId, String accountId);

  boolean existsByLedgerAndId(String ledgerId, String accountId);
}
