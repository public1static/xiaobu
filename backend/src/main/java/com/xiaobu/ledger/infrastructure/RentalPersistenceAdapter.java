package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.RentalContractResult;
import com.xiaobu.ledger.application.RentalExpiryReminderResult;
import com.xiaobu.ledger.application.RentalHomeResult;
import com.xiaobu.ledger.application.RentalPageResult;
import com.xiaobu.ledger.application.RentalRepository;
import com.xiaobu.ledger.domain.RentalContract;
import com.xiaobu.ledger.domain.RentalExpiryReminder;
import com.xiaobu.ledger.domain.RentalHome;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class RentalPersistenceAdapter implements RentalRepository {
  private static final int PAGE_SIZE = 20;
  private final RentalHomeJpaRepository homes;
  private final RentalContractJpaRepository contracts;

  RentalPersistenceAdapter(RentalHomeJpaRepository homes, RentalContractJpaRepository contracts) {
    this.homes = homes;
    this.contracts = contracts;
  }

  @Override
  public Optional<RentalHome> findHome(String ledgerId, String homeId) {
    return homes
        .findByLedgerIdAndIdAndDeletedFalse(ledgerId, homeId)
        .map(RentalHomeEntity::toDomain);
  }

  @Override
  public boolean existsOtherHomeWithName(String ledgerId, String homeId, String name) {
    return homes.existsByLedgerIdAndNameAndIdNotAndDeletedFalse(ledgerId, name, homeId);
  }

  @Override
  public void addHome(RentalHome home, String actorId) {
    homes.save(new RentalHomeEntity(home, actorId));
  }

  @Override
  public void updateHome(RentalHome home, String actorId) {
    homes
        .findByLedgerIdAndIdAndDeletedFalse(home.ledgerId(), home.id())
        .orElseThrow()
        .updateHome(home, actorId);
  }

  @Override
  public long countHomeContracts(String ledgerId, String homeId) {
    return contracts.countByLedgerIdAndHomeIdAndDeletedFalse(ledgerId, homeId);
  }

  @Override
  public RentalPageResult<RentalHomeResult> listHomes(String ledgerId, int page) {
    return toResult(
        homes
            .listHomes(ledgerId, PageRequest.of(page, PAGE_SIZE))
            .map(row -> RentalHomeResult.from(row.getHome().toDomain(), row.getContractCount())));
  }

  @Override
  public RentalPageResult<RentalContractResult> listContracts(
      String ledgerId, String homeId, int page) {
    return toResult(
        contracts
            .listContracts(ledgerId, homeId, PageRequest.of(page, PAGE_SIZE))
            .map(RentalPersistenceAdapter::toContractResult));
  }

  @Override
  public Optional<RentalContract> findContract(String ledgerId, String contractId) {
    return contracts
        .findByLedgerIdAndIdAndDeletedFalse(ledgerId, contractId)
        .map(RentalContractEntity::toDomain);
  }

  @Override
  public void linkBill(String ledgerId, String contractId, String billId, String actorId) {
    contracts
        .findByLedgerIdAndIdAndDeletedFalse(ledgerId, contractId)
        .orElseThrow()
        .linkBill(billId, actorId);
  }

  @Override
  public boolean hasOverlappingRentalBill(
      String ledgerId, String homeId, String contractId, LocalDate startsOn, LocalDate endsOn) {
    return contracts.countOverlappingRentalBills(ledgerId, homeId, contractId, startsOn, endsOn)
        > 0;
  }

  @Override
  public boolean hasContractForBill(String billId) {
    return contracts.existsByBillId(billId);
  }

  @Override
  public void addContract(RentalContract contract, String actorId) {
    contracts.save(new RentalContractEntity(contract, actorId));
  }

  @Override
  public boolean hasRenewal(String ledgerId, String contractId) {
    return contracts.existsByLedgerIdAndPreviousContractId(ledgerId, contractId);
  }

  @Override
  public boolean hasOverlappingContract(
      String ledgerId, String homeId, LocalDate startsOn, LocalDate endsOn, boolean renewalsOnly) {
    return contracts.countOverlappingContracts(ledgerId, homeId, startsOn, endsOn, renewalsOnly)
        > 0;
  }

  @Override
  public Optional<RentalContractResult> findContractDetails(String ledgerId, String contractId) {
    return contracts
        .findContractDetails(ledgerId, contractId)
        .map(RentalPersistenceAdapter::toContractResult);
  }

  @Override
  public void configureExpiryReminder(
      String ledgerId, String contractId, int remindDays, String actorId) {
    contracts
        .findByLedgerIdAndIdAndDeletedFalse(ledgerId, contractId)
        .orElseThrow()
        .configureExpiryReminder(remindDays, actorId);
  }

  @Override
  public void completeExpiryReminder(
      String ledgerId, String contractId, LocalDate endsOn, String actorId) {
    contracts
        .findByLedgerIdAndIdAndDeletedFalse(ledgerId, contractId)
        .orElseThrow()
        .completeExpiryReminder(endsOn, actorId);
  }

  @Override
  public RentalPageResult<RentalExpiryReminderResult> listExpiryReminders(
      String ledgerId, LocalDate today, boolean completed, int page) {
    return toResult(
        contracts.listExpiryReminders(ledgerId, today, completed, PageRequest.of(page, PAGE_SIZE)));
  }

  @Override
  public long countPendingExpiryReminders(String ledgerId, LocalDate today) {
    return contracts.countPendingExpiryReminders(
        ledgerId, today, today.plusDays(RentalExpiryReminder.DEFAULT_DAYS));
  }

  private static RentalContractResult toContractResult(
      RentalContractJpaRepository.ContractRow row) {
    var bill = row.getBill();
    return RentalContractResult.from(
        row.getContract().toDomain(),
        row.getHomeName(),
        bill == null ? null : bill.toDomain(),
        row.getNextContractId());
  }

  private static <T> RentalPageResult<T> toResult(Page<T> page) {
    return new RentalPageResult<>(
        page.getContent(), page.getTotalElements(), page.getNumber(), page.getSize());
  }
}
