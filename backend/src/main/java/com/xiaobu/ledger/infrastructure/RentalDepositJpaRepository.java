package com.xiaobu.ledger.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface RentalDepositJpaRepository extends Repository<RentalDepositEntity, String> {
  RentalDepositEntity save(RentalDepositEntity deposit);

  Optional<RentalDepositEntity> findByLedgerIdAndContractIdAndIdAndDeletedFalse(
      String ledgerId, String contractId, String id);

  String WHERE_CONTRACT =
      """
      where d.ledgerId = :ledgerId and d.contractId = :contractId and d.deleted = false
      """;
  String HELD_SIGN =
      "case when d.kind = com.xiaobu.ledger.domain.RentalDepositKind.PAY then d.amountCents else -d.amountCents end";

  @Query(
      value =
          """
          select d as deposit, a.name as accountName from RentalDepositEntity d
          left join LedgerAccountEntity a on a.id = d.accountId and a.ledgerId = d.ledgerId
          """
              + WHERE_CONTRACT
              + " order by d.bookedOn desc, d.id desc",
      countQuery = "select count(d.id) from RentalDepositEntity d " + WHERE_CONTRACT)
  Page<DepositRow> listDeposits(String ledgerId, String contractId, Pageable pageable);

  @Query("select coalesce(sum(" + HELD_SIGN + "), 0) from RentalDepositEntity d " + WHERE_CONTRACT)
  long sumHeld(String ledgerId, String contractId);

  @Query(
      "select d.bookedOn as bookedOn, sum("
          + HELD_SIGN
          + ") as changeCents from RentalDepositEntity d "
          + WHERE_CONTRACT
          + " and (:excludedId is null or d.id <> :excludedId) group by d.bookedOn")
  List<DepositDayRow> summarizeDailyChanges(String ledgerId, String contractId, String excludedId);

  @Query(
      "select d.accountId as accountId, -sum("
          + HELD_SIGN
          + ") as balanceCents "
          + "from RentalDepositEntity d where d.ledgerId = :ledgerId and d.deleted = false "
          + "and d.accountId is not null group by d.accountId")
  List<LedgerAccountBalanceRow> findAccountMovements(String ledgerId);

  interface DepositRow {
    RentalDepositEntity getDeposit();

    String getAccountName();
  }

  interface DepositDayRow {
    LocalDate getBookedOn();

    long getChangeCents();
  }
}
