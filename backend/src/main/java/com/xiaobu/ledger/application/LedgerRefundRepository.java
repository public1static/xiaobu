package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerRefund;
import java.time.LocalDate;
import java.util.List;

public interface LedgerRefundRepository {

  void add(LedgerRefund refund);

  List<LedgerRefund> findByTransaction(String ledgerId, String transactionId);

  List<LedgerRefundActivity> findActiveByDateRange(
      String ledgerId, LocalDate from, LocalDate through, String keyword);
}
