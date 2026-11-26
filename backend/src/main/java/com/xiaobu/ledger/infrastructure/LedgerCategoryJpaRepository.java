package com.xiaobu.ledger.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface LedgerCategoryJpaRepository extends Repository<LedgerCategoryEntity, String> {

  <S extends LedgerCategoryEntity> S save(S entity);

  List<LedgerCategoryEntity> findByLedgerId(String ledgerId);

  Optional<LedgerCategoryEntity> findByLedgerIdAndCode(String ledgerId, String code);
}
