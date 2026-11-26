package com.xiaobu.gift.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.gift.domain.GiftActivityBook;
import com.xiaobu.gift.domain.GiftBook;
import com.xiaobu.gift.domain.GiftContact;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class GiftDirectoryApplicationService {
  private static final IdPrefix BOOK = new IdPrefix("giftbook");
  private static final IdPrefix CONTACT = new IdPrefix("friend");
  private final PersonalLedgerApplicationService ledgers;
  private final GiftDirectoryRepository directory;
  private final GiftActivityQueryRepository activities;
  private final EntityIdGenerator ids;

  GiftDirectoryApplicationService(
      PersonalLedgerApplicationService ledgers,
      GiftDirectoryRepository directory,
      GiftActivityQueryRepository activities,
      EntityIdGenerator ids) {
    this.ledgers = ledgers;
    this.directory = directory;
    this.activities = activities;
    this.ids = ids;
  }

  @Transactional(readOnly = true)
  public List<GiftActivityBook> listBooks(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return directory.findBooks(ledgerId);
  }

  @Transactional
  public GiftActivityBook createBook(
      String ledgerId,
      String actorId,
      String rawName,
      TransactionDirection direction,
      LocalDate defaultDate,
      String rawReason) {
    ledgers.requireWriteAccess(ledgerId, actorId);
    String name = validateText(rawName, true);
    String reason = validateText(rawReason, false);
    if (GiftBook.dailyFor(direction).displayName().equalsIgnoreCase(name)
        || directory.findBooks(ledgerId).stream()
            .anyMatch(
                item -> item.direction() == direction && item.name().equalsIgnoreCase(name))) {
      throw conflict("该方向已有同名礼单");
    }
    var book =
        new GiftActivityBook(
            ids.next(BOOK), name, direction, defaultDate, reason.isEmpty() ? null : reason, false);
    directory.saveBook(ledgerId, actorId, book);
    return book;
  }

  @Transactional
  public void changeBookArchive(String ledgerId, String actorId, String bookId, boolean archived) {
    ledgers.requireWriteAccess(ledgerId, actorId);
    requireBook(ledgerId, bookId);
    directory.changeBookArchive(ledgerId, actorId, bookId, archived);
  }

  @Transactional(readOnly = true)
  public List<GiftContact> listContacts(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return directory.findContacts(ledgerId);
  }

  @Transactional
  public GiftContact createContact(
      String ledgerId, String actorId, String rawName, String rawDistinction) {
    ledgers.requireWriteAccess(ledgerId, actorId);
    String name = validateText(rawName, true);
    String distinction = validateText(rawDistinction, false);
    for (var row : directory.findContacts(ledgerId)) {
      if (!row.name().equalsIgnoreCase(name)) continue;
      if (distinction.isEmpty() || row.distinction().equalsIgnoreCase(distinction)) {
        throw conflict("请填写未被同名亲友使用的称呼或关系");
      }
    }
    var contact = new GiftContact(ids.next(CONTACT), name, distinction);
    directory.saveContact(ledgerId, actorId, contact);
    return contact;
  }

  @Transactional(readOnly = true)
  public List<GiftActivityResult> listActivities(
      String ledgerId, String actorId, String book, String contact) {
    ledgers.requireReadAccess(ledgerId, actorId);
    String activity = null;
    String daily = null;
    if (book != null) {
      if (GiftBook.DAILY_SENT.name().equals(book) || GiftBook.DAILY_RECEIVED.name().equals(book))
        daily = book;
      else activity = requireBook(ledgerId, book).id();
    }
    if (contact != null) requireContact(ledgerId, contact);
    return activities.findRecent(ledgerId, activity, daily, contact);
  }

  GiftActivityBook requireBook(String ledgerId, String bookId) {
    return directory.findBook(ledgerId, bookId).orElseThrow(() -> invalid("请选择当前账本的礼单"));
  }

  GiftContact requireContact(String ledgerId, String contactId) {
    return directory.findContact(ledgerId, contactId).orElseThrow(() -> invalid("请选择当前账本的亲友"));
  }

  private static String validateText(String raw, boolean required) {
    String text = raw == null ? "" : raw.strip();
    if ((required && text.isEmpty()) || text.length() > 60) throw invalid("请输入有效名称或说明，最多 60 字");
    return text;
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_GIFT_DIRECTORY_INVALID"), message);
  }

  private static FoundationException conflict(String message) {
    return new FoundationException(
        new ErrorCode("XIAOBU_GIFT_DIRECTORY_CONFLICT"), ErrorCategory.CONFLICT, message, Map.of());
  }
}
