package com.xiaobu.gift.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "gift_reasons")
class GiftReasonEntity {
  // 数据库生成的目录行编号。
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 拥有此目录的账本。
  @Column(name = "ledger_id", nullable = false)
  private String ledgerId;

  // 流水使用的事由名称。
  @Column(nullable = false)
  private String name;

  // 同一账本内从零开始的显示位置。
  @Column(name = "display_order", nullable = false)
  private int displayOrder;

  protected GiftReasonEntity() {}

  // 将事由放入指定账本的目录位置。
  GiftReasonEntity(String ledgerId, String name, int position) {
    this.ledgerId = ledgerId;
    this.name = name;
    this.displayOrder = position;
  }
}
