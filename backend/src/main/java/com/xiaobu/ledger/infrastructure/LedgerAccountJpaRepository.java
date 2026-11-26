package com.xiaobu.ledger.infrastructure;

import java.util.List;
import org.springframework.data.repository.Repository;

interface LedgerAccountJpaRepository extends Repository<LedgerAccountEntity, String> {

  <S extends LedgerAccountEntity> S save(S entity);

  List<LedgerAccountEntity> findByLedgerIdOrderByNameAscIdAsc(String ledgerId);

  boolean existsByLedgerIdAndId(String ledgerId, String id);

  boolean existsByLedgerIdAndName(String ledgerId, String name);
}
