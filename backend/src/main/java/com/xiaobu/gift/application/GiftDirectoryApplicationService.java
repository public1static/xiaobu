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
  private final PersonalLedgerApplicationService ledgers;
  private final GiftDirectoryRepository directory;
  private final GiftActivityQueryRepository activities;
  private final EntityIdGenerator ids;
  private final LedgerContactApplicationService contacts;

  GiftDirectoryApplicationService(
      PersonalLedgerApplicationService ledgers,
      GiftDirectoryRepository directory,
      GiftActivityQueryRepository activities,
      EntityIdGenerator ids,
      LedgerContactApplicationService contacts) {
    this.ledgers = ledgers;
    this.directory = directory;
    this.activities = activities;
    this.ids = ids;
    this.contacts = contacts;
  }

  // 未自定义账本使用原型中的常用事由顺序。
  private static final List<String> DEFAULT_REASONS =
      List.of(
          "婚礼", "生日", "满月", "乔迁", "升学", "节日", "探望", "订婚", "百日", "周岁", "寿宴", "开业", "毕业", "慰问", "丧事");

  // 已有记录保留各自的事由文字，目录仅供新录入时选用。
  @Transactional(readOnly = true)
  public List<String> listGiftReasons(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return currentReasons(ledgerId);
  }

  // 在账本写锁下比较修改前的目录，避免覆盖另一成员的新增或排序。
  @Transactional
  public List<String> saveGiftReasons(SaveGiftReasonsCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var current = currentReasons(command.ledgerId());
    var names = command.names().stream().map(n -> validateText(n, true)).toList();
    if (names.stream().map(n -> n.toLowerCase(java.util.Locale.ROOT)).distinct().count()
        != names.size()) {
      throw invalid("已有同名事由");
    }
    if (names.equals(current)) {
      return current;
    }
    if (!current.equals(command.previous())) {
      throw conflict("事由目录已更新，请重新选择");
    }
    if (!names.containsAll(current) || names.size() > current.size() + 1) {
      throw invalid("请逐项新增或调整已有事由顺序");
    }
    directory.saveReasons(command.ledgerId(), names);
    return names;
  }

  // 首次写入之前只读取默认目录。
  private List<String> currentReasons(String ledgerId) {
    var names = directory.findReasons(ledgerId);
    return names.isEmpty() ? DEFAULT_REASONS : names;
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
    return directory.findGiftContacts(ledgerId).stream().map(GiftContactResult::from).toList();
  }

  @Transactional
  public GiftContactResult createGiftContact(CreateGiftContactCommand command) {
    return GiftContactResult.from(contacts.registerGiftContact(command));
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
    if (keyword.length() > 100) {
      throw invalid("搜索词最多 100 字");
    }
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
    if (query.contactId() != null) {
      requireContact(ledgerId, query.contactId());
    }
    var filter = new GiftActivityFilter(ledgerId, activity, daily, query.contactId(), keyword);
    var totals = activities.findTotals(filter);
    long count = 0;
    long income = 0;
    long expense = 0;
    for (var total : totals) {
      count += total.entryCount();
      if (total.direction() == TransactionDirection.INCOME) {
        income = total.amountCents();
      } else {
        expense = total.amountCents();
      }
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
    if ((required && text.isEmpty()) || text.length() > 60) {
      throw invalid("请输入有效名称或说明，最多 60 字");
    }
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
