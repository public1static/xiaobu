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

  @Column(name = "gift_member", nullable = false)
  private boolean giftMember;

  protected GiftContactEntity() {}

  GiftContactEntity(String ledgerId, String actorId, GiftContact contact) {
    super(contact.id(), ledgerId, actorId);
    name = contact.name();
    distinction = contact.distinction();
    giftMember = contact.giftMember();
  }

  void markGiftMember(String actorId) {
    giftMember = true;
    updatedBy = actorId;
  }

  GiftContact toDomain() {
    return new GiftContact(getId(), name, distinction, giftMember);
  }
}
