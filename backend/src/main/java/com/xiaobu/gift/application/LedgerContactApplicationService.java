package com.xiaobu.gift.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.gift.domain.GiftContact;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class LedgerContactApplicationService {
  private static final IdPrefix CONTACT = new IdPrefix("friend");
  private final GiftDirectoryRepository contacts;
  private final PersonalLedgerApplicationService ledgers;
  private final EntityIdGenerator ids;

  LedgerContactApplicationService(
      GiftDirectoryRepository contacts,
      PersonalLedgerApplicationService ledgers,
      EntityIdGenerator ids) {
    this.contacts = contacts;
    this.ledgers = ledgers;
    this.ids = ids;
  }

  @Transactional(readOnly = true)
  public LedgerContactPageResult listContactMatches(
      String ledgerId, String actorId, String name, int page) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return contacts.listContactMatches(ledgerId, clean(name, true), page);
  }

  @Transactional
  public GiftContactResult resolveRentalContact(
      String ledgerId, String actorId, String contactId, String name, String distinction) {
    ledgers.requireWriteAccess(ledgerId, actorId);
    if (contactId != null) return GiftContactResult.from(requireContact(ledgerId, contactId));
    name = clean(name, true);
    distinction = clean(distinction, false);
    var matches = contacts.findContactsByName(ledgerId, name);
    if (distinction.isEmpty()) {
      if (matches.size() > 1) {
        throw contactConflict("有多位同名联系人，请重新选择");
      }
      if (matches.size() == 1) {
        return GiftContactResult.from(matches.getFirst());
      }
    }
    rejectDuplicateDistinction(matches, distinction);
    return GiftContactResult.from(addContact(ledgerId, actorId, name, distinction, false));
  }

  // Called within the gift transaction. Promotion rolls back if the gift itself fails.
  GiftContact selectGiftContact(String ledgerId, String actorId, String contactId) {
    var contact = requireContact(ledgerId, contactId);
    if (!contact.giftMember()) contacts.markGiftMember(ledgerId, contact.id(), actorId);
    return contact;
  }

  GiftContact registerGiftContact(CreateGiftContactCommand command) {
    String ledgerId = command.ledgerId();
    String actorId = command.actorId();
    ledgers.requireWriteAccess(ledgerId, actorId);
    String name = clean(command.name(), true);
    String distinction = clean(command.distinction(), false);
    var matches = contacts.findContactsByName(ledgerId, name);
    if (matches.size() == 1 && distinction.isEmpty() && !matches.getFirst().giftMember()) {
      return selectGiftContact(ledgerId, actorId, matches.getFirst().id());
    }
    if (!matches.isEmpty() && distinction.isEmpty()) {
      throw giftConflict("同名联系人需要填写不同的称呼或关系");
    }
    if (matches.stream().anyMatch(row -> row.distinction().equalsIgnoreCase(distinction))) {
      throw giftConflict("称呼或关系已使用");
    }
    return addContact(ledgerId, actorId, name, distinction, true);
  }

  private GiftContact requireContact(String ledgerId, String contactId) {
    return contacts
        .findContact(ledgerId, contactId)
        .orElseThrow(() ->
            new FoundationException(new ErrorCode("XIAOBU_LEDGER_CONTACT_INVALID"), "请选择当前账本的联系人"));
  }

  private GiftContact addContact(
      String ledgerId, String actorId, String name, String distinction, boolean giftMember) {
    var contact = new GiftContact(ids.next(CONTACT), name, distinction, giftMember);
    contacts.saveContact(ledgerId, actorId, contact);
    return contact;
  }

  private static void rejectDuplicateDistinction(List<GiftContact> matches, String distinction) {
    if (matches.stream().anyMatch(row -> row.distinction().equalsIgnoreCase(distinction))) {
      throw contactConflict("同名联系人的称呼或关系已被使用");
    }
  }

  private static String clean(String raw, boolean required) {
    String text = raw == null ? "" : raw.strip();
    if ((required && text.isEmpty()) || text.length() > 60) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_LEDGER_CONTACT_INVALID"), "请输入有效姓名或称呼，最多 60 字");
    }
    return text;
  }

  private static FoundationException contactConflict(String message) {
    return new FoundationException(
        new ErrorCode("XIAOBU_LEDGER_CONTACT_CONFLICT"), ErrorCategory.CONFLICT, message, Map.of());
  }

  private static FoundationException giftConflict(String message) {
    return new FoundationException(
        new ErrorCode("XIAOBU_GIFT_DIRECTORY_CONFLICT"), ErrorCategory.CONFLICT, message, Map.of());
  }
}
