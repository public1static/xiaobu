package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalDeposit;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

public interface RentalDepositRepository {
  record Entry(RentalDeposit deposit, String accountName) {}

  RentalPageResult<Entry> listDeposits(String ledgerId, String contractId, int page);

  long sumHeld(String ledgerId, String contractId);

  Optional<RentalDeposit> findDeposit(String ledgerId, String contractId, String depositId);

  Map<LocalDate, Long> summarizeDailyChanges(String ledgerId, String contractId, String excludedId);

  void addDeposit(RentalDeposit deposit);

  void updateDeposit(RentalDeposit deposit, String actorId);
}
