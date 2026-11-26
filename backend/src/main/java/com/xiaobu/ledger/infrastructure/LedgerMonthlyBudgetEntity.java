package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.data.jpa.AuditableEntity;
import com.xiaobu.ledger.domain.LedgerMonthlyBudget;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "ledger_monthly_budgets")
public class LedgerMonthlyBudgetEntity extends AuditableEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(name = "month_start", nullable = false, updatable = false)
  private LocalDate monthStart;

  @Column(name = "amount_cents", nullable = false)
  private long amountCents;

  @Column(name = "created_by", nullable = false, length = 48, updatable = false)
  private String createdBy;

  @Column(name = "updated_by", nullable = false, length = 48)
  private String updatedBy;

  @Column(nullable = false)
  private boolean deleted;

  @Transient private boolean newEntity = true;

  protected LedgerMonthlyBudgetEntity() {}

  LedgerMonthlyBudgetEntity(String id, LedgerMonthlyBudget budget, String actorId) {
    this.id = id;
    ledgerId = budget.ledgerId();
    monthStart = budget.month().atDay(1);
    createdBy = actorId;
    revise(budget.amountCents(), actorId);
  }

  void revise(long cents, String actorId) {
    amountCents = cents;
    updatedBy = actorId;
    deleted = false;
  }

  LedgerMonthlyBudget toDomain() {
    return new LedgerMonthlyBudget(ledgerId, YearMonth.from(monthStart), amountCents);
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  private void markPersisted() {
    newEntity = false;
  }
}
