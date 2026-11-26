package com.xiaobu.gift.infrastructure;

import com.xiaobu.gift.domain.GiftContact;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "gift_contacts")
class GiftContactEntity extends GiftDirectoryEntity {
  @Column(nullable = false, length = 60)
  private String name;

  @Column(nullable = false, length = 60)
  private String distinction;

  protected GiftContactEntity() {}

  GiftContactEntity(String ledgerId, String actorId, GiftContact contact) {
    super(contact.id(), ledgerId, actorId);
    name = contact.name();
    distinction = contact.distinction();
  }

  GiftContact toDomain() {
    return new GiftContact(getId(), name, distinction);
  }
}
