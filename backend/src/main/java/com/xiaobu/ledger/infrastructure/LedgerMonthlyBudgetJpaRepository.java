package com.xiaobu.ledger.infrastructure;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface LedgerMonthlyBudgetJpaRepository extends Repository<LedgerMonthlyBudgetEntity, String> {

  Optional<LedgerMonthlyBudgetEntity> findByLedgerIdAndMonthStartAndDeletedFalse(
      String ledgerId, LocalDate monthStart);

  Optional<LedgerMonthlyBudgetEntity> findByLedgerIdAndMonthStart(
      String ledgerId, LocalDate monthStart);

  LedgerMonthlyBudgetEntity save(LedgerMonthlyBudgetEntity budget);
}
