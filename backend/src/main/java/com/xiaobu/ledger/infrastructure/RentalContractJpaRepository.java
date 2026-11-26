package com.xiaobu.ledger.infrastructure;

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

  String CONTRACTS =
      """
      from RentalContractEntity c
      join RentalHomeEntity h on h.id = c.homeId and h.ledgerId = c.ledgerId and h.deleted = false
      left join FixedBillEntity b on b.id = c.billId and b.ledgerId = c.ledgerId and b.deleted = false
      where c.ledgerId = :ledgerId and c.deleted = false and (:homeId is null or c.homeId = :homeId)
      """;

  @Query(
      value =
          "select c as contract, h.name as homeName, b as bill "
              + CONTRACTS
              + " order by c.startsOn desc, c.id desc",
      countQuery = "select count(c.id) " + CONTRACTS)
  Page<ContractRow> listContracts(String ledgerId, String homeId, Pageable pageable);

  interface ContractRow {
    RentalContractEntity getContract();

    String getHomeName();

    FixedBillEntity getBill();
  }
}
