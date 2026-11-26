package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerRefund;
import java.util.List;

public interface LedgerRefundRepository {

  void add(LedgerRefund refund);

  List<LedgerRefund> findByTransaction(String ledgerId, String transactionId);
}
