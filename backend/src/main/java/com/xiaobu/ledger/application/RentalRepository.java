package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalContract;
import com.xiaobu.ledger.domain.RentalHome;
import java.time.LocalDate;
import java.util.Optional;

public interface RentalRepository {
  Optional<RentalHome> findHome(String ledgerId, String homeId);

  boolean existsOtherHomeWithName(String ledgerId, String homeId, String name);

  void addHome(RentalHome home, String actorId);

  void updateHome(RentalHome home, String actorId);

  long countHomeContracts(String ledgerId, String homeId);

  RentalPageResult<RentalHomeResult> listHomes(String ledgerId, int page);

  RentalPageResult<RentalContractResult> listContracts(String ledgerId, String homeId, int page);

  Optional<RentalContract> findContract(String ledgerId, String contractId);

  void linkBill(String ledgerId, String contractId, String billId, String actorId);

  boolean hasOverlappingRentalBill(
      String ledgerId, String homeId, String contractId, LocalDate startsOn, LocalDate endsOn);

  boolean hasContractForBill(String billId);

  boolean hasRenewal(String ledgerId, String contractId);

  boolean hasOverlappingContract(
      String ledgerId, String homeId, LocalDate startsOn, LocalDate endsOn, boolean renewalsOnly);

  Optional<RentalContractResult> findContractDetails(String ledgerId, String contractId);

  void configureExpiryReminder(String ledgerId, String contractId, int remindDays, String actorId);

  void completeExpiryReminder(String ledgerId, String contractId, LocalDate endsOn, String actorId);

  RentalPageResult<RentalExpiryReminderResult> listExpiryReminders(
      String ledgerId, LocalDate today, boolean completed, int page);

  long countPendingExpiryReminders(String ledgerId, LocalDate today);

  void addContract(RentalContract contract, String actorId);
}
