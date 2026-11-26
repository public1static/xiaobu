package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerCategoryRepository;
import com.xiaobu.ledger.domain.LedgerCategory;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class LedgerCategoryPersistenceAdapter implements LedgerCategoryRepository {

  private final LedgerCategoryJpaRepository categories;
  private final LedgerCategoryPersistenceMapper mapper;

  LedgerCategoryPersistenceAdapter(
      LedgerCategoryJpaRepository categories, LedgerCategoryPersistenceMapper mapper) {
    this.categories = categories;
    this.mapper = mapper;
  }

  @Override
  public List<LedgerCategory> findByLedger(String ledgerId) {
    return categories.findByLedgerId(ledgerId).stream().map(mapper::toDomain).toList();
  }

  @Override
  public void save(LedgerCategory category) {
    categories
        .findByLedgerIdAndCode(category.ledgerId(), category.code())
        .ifPresentOrElse(
            entity -> entity.configure(category.displayOrder(), category.active()),
            () -> categories.save(mapper.toNewEntity(category)));
  }
}
