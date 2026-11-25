package com.xiaobu.ledger.infrastructure;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ExpenseJpaRepository extends JpaRepository<ExpenseEntity, String> {

  @Modifying
  @Query(
      value =
          """
          INSERT INTO ledger_transactions
              (id, ledger_id, direction, amount_cents, booked_on, category_code, note,
               created_by, created_at, updated_by, updated_at, deleted)
          VALUES (:id, :ledger, 'EXPENSE', :cents, :bookedOn, :categoryCode, :note,
                  :actor, UTC_TIMESTAMP(6), :actor, UTC_TIMESTAMP(6), 0)
          """,
      nativeQuery = true)
  void insert(
      String id,
      String ledger,
      String actor,
      long cents,
      LocalDate bookedOn,
      String categoryCode,
      String note);

  @Query(
      value =
          """
          SELECT id, amount_cents, booked_on, category_code, note FROM ledger_transactions
          WHERE ledger_id = :ledger AND created_by = :actor AND direction = 'EXPENSE'
              AND deleted = 0 AND booked_on BETWEEN :start AND :end
          ORDER BY booked_on DESC, id DESC
          """,
      nativeQuery = true)
  List<ExpenseEntity> listByPeriod(String ledger, String actor, LocalDate start, LocalDate end);
}
