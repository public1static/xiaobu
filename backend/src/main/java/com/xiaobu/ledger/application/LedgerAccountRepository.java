package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerAccount;
import java.util.List;
import java.util.Map;

public interface LedgerAccountRepository {

  void add(LedgerAccount account);

  List<LedgerAccount> findByLedger(String ledgerId);

  Map<String, Long> findRecordedBalancesByLedger(String ledgerId);

  boolean existsByLedgerAndId(String ledgerId, String accountId);
}
