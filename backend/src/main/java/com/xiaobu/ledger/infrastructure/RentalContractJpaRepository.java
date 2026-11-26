package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.RentalExpiryReminderResult;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface RentalContractJpaRepository extends Repository<RentalContractEntity, String> {
  RentalContractEntity save(RentalContractEntity contract);

  Optional<RentalContractEntity> findByLedgerIdAndIdAndDeletedFalse(String ledgerId, String id);

  long countByLedgerIdAndHomeIdAndDeletedFalse(String ledgerId, String homeId);

  @Query(
      """
      select count(c.id) from RentalContractEntity c
      where c.ledgerId = :ledgerId and c.homeId = :homeId and c.id <> :excludedId
          and c.billId is not null and c.deleted = false
          and c.startsOn <= :endsOn and c.endsOn >= :startsOn
      """)
  long countOverlappingRentalBills(
      String ledgerId, String homeId, String excludedId, LocalDate startsOn, LocalDate endsOn);

  boolean existsByBillId(String billId);

  boolean existsByLedgerIdAndPreviousContractId(String ledgerId, String previousContractId);

  @Query(
      """
      select count(c.id) from RentalContractEntity c
      where c.ledgerId = :ledgerId and c.homeId = :homeId and c.deleted = false
          and c.startsOn <= :endsOn and c.endsOn >= :startsOn
          and (:renewalsOnly = false or c.previousContractId is not null)
      """)
  long countOverlappingContracts(
      String ledgerId, String homeId, LocalDate startsOn, LocalDate endsOn, boolean renewalsOnly);

  String CONTRACTS =
      """
      from RentalContractEntity c
      join RentalHomeEntity h on h.id = c.homeId and h.ledgerId = c.ledgerId and h.deleted = false
      left join FixedBillEntity b on b.id = c.billId and b.ledgerId = c.ledgerId and b.deleted = false
      left join RentalContractEntity n on n.ledgerId = c.ledgerId and n.previousContractId = c.id and n.deleted = false
      where c.ledgerId = :ledgerId and c.deleted = false
      """;

  String CONTRACT_DETAILS =
      "select c as contract, h.name as homeName, b as bill, n.id as nextContractId ";

  @Query(CONTRACT_DETAILS + CONTRACTS + " and c.id = :contractId")
  Optional<ContractRow> findContractDetails(String ledgerId, String contractId);

  String CONTRACT_LIST = CONTRACTS + " and (:homeId is null or c.homeId = :homeId)";

  @Query(
      value = CONTRACT_DETAILS + CONTRACT_LIST + " order by c.startsOn desc, c.id desc",
      countQuery = "select count(c.id) " + CONTRACT_LIST)
  Page<ContractRow> listContracts(String ledgerId, String homeId, Pageable pageable);

  String UNHANDLED = "(c.handledEnd is null or c.handledEnd <> c.endsOn)";
  String TRIGGERED = "(timestampadd(day, -c.remindDays, c.endsOn) <= :today)";
  String EXPIRIES =
      """
      from RentalContractEntity c
      join RentalHomeEntity h on h.id = c.homeId and h.ledgerId = c.ledgerId and h.deleted = false
      where c.ledgerId = :ledgerId and c.deleted = false
      """;
  String REMINDER_FILTER =
      EXPIRIES
          + " and ((:completed = true and c.handledEnd = c.endsOn)"
          + " or (:completed = false and "
          + UNHANDLED
          + "))";

  @Query(
      value =
          """
          select new com.xiaobu.ledger.application.RentalExpiryReminderResult(
              c.id, h.name, c.landlordName, c.endsOn, c.remindDays, c.handledEnd)
          """
              + REMINDER_FILTER
              + " order by case when :completed = false and "
              + TRIGGERED
              + " then 0 else 1 end,"
              + " case when :completed = false then c.endsOn else null end asc,"
              + " case when :completed = true then c.endsOn else null end desc, c.id",
      countQuery = "select count(c.id) " + REMINDER_FILTER)
  Page<RentalExpiryReminderResult> listExpiryReminders(
      String ledgerId, LocalDate today, boolean completed, Pageable pageable);

  @Query(
      "select count(c.id) "
          + EXPIRIES
          + " and c.endsOn <= :latestDueOn and "
          + UNHANDLED
          + " and "
          + TRIGGERED)
  long countPendingExpiryReminders(String ledgerId, LocalDate today, LocalDate latestDueOn);

  interface ContractRow {
    RentalContractEntity getContract();

    String getHomeName();

    FixedBillEntity getBill();

    String getNextContractId();
  }
}
