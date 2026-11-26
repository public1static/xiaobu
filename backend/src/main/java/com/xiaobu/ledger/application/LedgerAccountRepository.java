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

  // 按账户编号修改资料，保留历史流水关联。
  void update(LedgerAccount account);

  // 返回全部日期的资金变动，按实际业务日倒序。
  List<LedgerAccountHistoryResult> findHistory(String ledgerId, String accountId, String actorId);
}
