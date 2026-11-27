package com.xiaobu.ledger.infrastructure;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface RentalHomeJpaRepository extends Repository<RentalHomeEntity, String> {
  RentalHomeEntity save(RentalHomeEntity home);

  Optional<RentalHomeEntity> findByLedgerIdAndIdAndDeletedFalse(String ledgerId, String id);

  boolean existsByLedgerIdAndNameAndIdNotAndDeletedFalse(String ledgerId, String name, String id);

  String HOMES = " from RentalHomeEntity h where h.ledgerId = :ledgerId and h.deleted = false";

  @Query(
      value =
          """
          select h as home, (select count(c.id) from RentalContractEntity c
              where c.ledgerId = h.ledgerId and c.homeId = h.id and c.deleted = false) as contractCount
          """
              + HOMES
              + " order by h.id desc",
      countQuery = "select count(h.id)" + HOMES)
  Page<HomeRow> listHomes(String ledgerId, Pageable pageable);

  interface HomeRow {
    RentalHomeEntity getHome();

    long getContractCount();
  }
}
