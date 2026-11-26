package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.RentalDepositRepository;
import com.xiaobu.ledger.application.RentalPageResult;
import com.xiaobu.ledger.domain.RentalDeposit;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class RentalDepositPersistenceAdapter implements RentalDepositRepository {
  private static final int PAGE_SIZE = 20;
  private final RentalDepositJpaRepository deposits;

  RentalDepositPersistenceAdapter(RentalDepositJpaRepository deposits) {
    this.deposits = deposits;
  }

  @Override
  public RentalPageResult<Entry> listDeposits(String ledgerId, String contractId, int page) {
    var rows = deposits.listDeposits(ledgerId, contractId, PageRequest.of(page, PAGE_SIZE));
    return new RentalPageResult<>(
        rows.map(row -> new Entry(row.getDeposit().toDomain(), row.getAccountName())).getContent(),
        rows.getTotalElements(),
        page,
        PAGE_SIZE);
  }

  @Override
  public long sumHeld(String ledgerId, String contractId) {
    return deposits.sumHeld(ledgerId, contractId);
  }

  @Override
  public Optional<RentalDeposit> findDeposit(String ledgerId, String contractId, String depositId) {
    return deposits
        .findByLedgerIdAndContractIdAndIdAndDeletedFalse(ledgerId, contractId, depositId)
        .map(RentalDepositEntity::toDomain);
  }

  @Override
  public Map<LocalDate, Long> summarizeDailyChanges(
      String ledgerId, String contractId, String excludedId) {
    return deposits.summarizeDailyChanges(ledgerId, contractId, excludedId).stream()
        .collect(
            Collectors.toUnmodifiableMap(
                RentalDepositJpaRepository.DepositDayRow::getBookedOn,
                RentalDepositJpaRepository.DepositDayRow::getChangeCents));
  }

  @Override
  public void addDeposit(RentalDeposit deposit) {
    deposits.save(new RentalDepositEntity(deposit));
  }

  @Override
  public void updateDeposit(RentalDeposit deposit, String actorId) {
    deposits
        .findByLedgerIdAndContractIdAndIdAndDeletedFalse(
            deposit.ledgerId(), deposit.contractId(), deposit.id())
        .orElseThrow()
        .revise(deposit, actorId);
  }
}
