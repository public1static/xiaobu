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
  public List<GiftBookResult> listGiftBooks(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return directory.findBooks(ledgerId).stream().map(GiftBookResult::from).toList();
  }

  @Transactional
  public GiftBookResult createGiftBook(CreateGiftBookCommand command) {
    String ledgerId = command.ledgerId();
    String actorId = command.actorId();
    TransactionDirection direction = command.direction();
    ledgers.requireWriteAccess(ledgerId, actorId);
    String name = validateText(command.name(), true);
    String reason = validateText(command.defaultReason(), false);
    if (GiftBook.dailyFor(direction).displayName().equalsIgnoreCase(name)
        || directory.findBooks(ledgerId).stream()
            .anyMatch(
                item -> item.direction() == direction && item.name().equalsIgnoreCase(name))) {
      throw conflict("该方向已有同名礼单");
    }
    var book =
        new GiftActivityBook(
            ids.next(BOOK),
            name,
            direction,
            command.defaultDate(),
            reason.isEmpty() ? null : reason,
            false);
    directory.saveBook(ledgerId, actorId, book);
    return GiftBookResult.from(book);
  }

  @Transactional
  public void changeGiftBookArchive(ChangeGiftBookArchiveCommand command) {
    String ledgerId = command.ledgerId();
    String actorId = command.actorId();
    String bookId = command.bookId();
    boolean archived = command.archived();
    ledgers.requireWriteAccess(ledgerId, actorId);
    requireBook(ledgerId, bookId);
    directory.changeBookArchive(ledgerId, actorId, bookId, archived);
  }

  @Transactional(readOnly = true)
  public List<GiftContactResult> listGiftContacts(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return directory.findContacts(ledgerId).stream().map(GiftContactResult::from).toList();
  }

  @Transactional
  public GiftContactResult createGiftContact(CreateGiftContactCommand command) {
    return GiftContactResult.from(registerContact(command));
  }

  // Inline registration runs in the already-open transaction of the main ledger write.
  GiftContact registerContact(CreateGiftContactCommand command) {
    String ledgerId = command.ledgerId();
    String actorId = command.actorId();
    ledgers.requireWriteAccess(ledgerId, actorId);
    String name = validateText(command.name(), true);
    String distinction = validateText(command.distinction(), false);
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
  public GiftActivityPageResult listGiftActivities(ListGiftActivitiesQuery query) {
    String ledgerId = query.ledgerId();
    ledgers.requireReadAccess(ledgerId, query.actorId());
    if (query.page() < 0
        || query.pageSize() < 1
        || query.pageSize() > 100
        || (long) query.page() * query.pageSize() > Integer.MAX_VALUE) {
      throw invalid("分页参数无效");
    }
    String keyword = query.keyword() == null ? "" : query.keyword().strip();
    if (keyword.length() > 100) throw invalid("搜索词最多 100 字");
    String activity = null;
    GiftBook daily = null;
    if (query.bookId() != null) {
      if (GiftBook.DAILY_SENT.name().equals(query.bookId())
          || GiftBook.DAILY_RECEIVED.name().equals(query.bookId())) {
        daily = GiftBook.valueOf(query.bookId());
      } else {
        activity = requireBook(ledgerId, query.bookId()).id();
      }
    }
    if (query.contactId() != null) requireContact(ledgerId, query.contactId());
    var filter = new GiftActivityFilter(ledgerId, activity, daily, query.contactId(), keyword);
    var totals = activities.findTotals(filter);
    long count = 0;
    long income = 0;
    long expense = 0;
    for (var total : totals) {
      count += total.entryCount();
      if (total.direction() == TransactionDirection.INCOME) income = total.amountCents();
      else expense = total.amountCents();
    }
    var items =
        (long) query.page() * query.pageSize() < count
            ? activities.findPage(filter, query.page(), query.pageSize())
            : List.<GiftActivityResult>of();
    return new GiftActivityPageResult(
        items, income, expense, count, query.page(), query.pageSize());
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
