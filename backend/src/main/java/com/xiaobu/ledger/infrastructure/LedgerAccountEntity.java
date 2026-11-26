package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.domain.LedgerAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccountEntity implements Persistable<String> {

  @Id
  @Column(length = 48, updatable = false)
  private String id;

  @Column(name = "ledger_id", nullable = false, length = 48, updatable = false)
  private String ledgerId;

  @Column(nullable = false, length = 40)
  private String name;

  @Column(name = "opening_balance_cents", nullable = false)
  private long openingBalanceCents;

  // 用途决定账户的展示分组和金额含义。
  @Column(name = "account_type", nullable = false, length = 16)
  private String type;

  // 停用账户仍保留历史关联。
  @Column(nullable = false)
  private boolean active;

  // 尚未填写的余额区别于真实零余额。
  @Column(name = "balance_known", nullable = false)
  private boolean balanceKnown;

  @Transient private boolean newEntity = true;

  protected LedgerAccountEntity() {}

  public LedgerAccountEntity(
      String id,
      String ledgerId,
      String name,
      long openingBalanceCents,
      String type,
      boolean active,
      boolean balanceKnown) {
    this.type = type;
    this.active = active;
    this.balanceKnown = balanceKnown;
    this.id = id;
    this.ledgerId = ledgerId;
    this.name = name;
    this.openingBalanceCents = openingBalanceCents;
  }

  @Override
  public String getId() {
    return id;
  }

  public String getLedgerId() {
    return ledgerId;
  }

  public String getName() {
    return name;
  }

  public long getOpeningBalanceCents() {
    return openingBalanceCents;
  }

  @Override
  public boolean isNew() {
    return newEntity;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    newEntity = false;
  }

  // 读取账户用途。
  public String getType() {
    return type;
  }

  // 查询是否可用于新流水。
  public boolean isActive() {
    return active;
  }

  // 判断余额是否已设置。
  public boolean isBalanceKnown() {
    return balanceKnown;
  }

  // 保持账户主键和历史关联，仅更新可维护的资料。
  void revise(LedgerAccount account) {
    type = account.type();
    active = account.active();
    balanceKnown = account.balanceKnown();
    openingBalanceCents = account.openingBalanceCents();
    name = account.name();
  }
}
