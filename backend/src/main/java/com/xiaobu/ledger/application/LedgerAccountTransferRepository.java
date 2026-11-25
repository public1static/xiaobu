package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerAccountTransfer;
import java.util.List;
import java.util.Map;

public interface LedgerAccountTransferRepository {

  void add(LedgerAccountTransfer transfer);

  Map<String, Long> findNetByLedger(String ledgerId);

  List<LedgerAccountTransfer> findRecentByLedger(String ledgerId);
}
