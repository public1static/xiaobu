package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.Expense;
import com.xiaobu.ledger.domain.ExpenseDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "ledger_transactions")
public class ExpenseEntity {

  @Id private String id;

  @Column(name = "amount_cents", nullable = false)
  private long amountCents;

  @Column(name = "booked_on", nullable = false)
  private LocalDate bookedOn;

  @Column(name = "category_code", nullable = false)
  private String categoryCode;

  @Column(nullable = false)
  private String note;

  protected ExpenseEntity() {}

  Expense toDomain() {
    return new Expense(id, new ExpenseDetails(amountCents, bookedOn, categoryCode, note));
  }
}
