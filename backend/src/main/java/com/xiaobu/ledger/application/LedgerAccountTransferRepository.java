package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerAccountTransfer;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface LedgerAccountTransferRepository {

  void add(LedgerAccountTransfer transfer);

  Map<String, Long> findNetByLedger(String ledgerId);

  List<LedgerAccountTransfer> findRecentByLedger(String ledgerId);

  // 查找当前账本中可维护的转账。
  Optional<LedgerAccountTransfer> findByLedgerAndId(String ledgerId, String id);

  // 修改同一笔转账的两个资金端。
  void update(LedgerAccountTransfer transfer);

  // 撤销该笔转账的全部余额影响。
  void cancel(String ledgerId, String id);
}
