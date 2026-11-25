package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerCategory;
import com.xiaobu.ledger.domain.LedgerCategoryDefaults;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class LedgerCategoryApplicationService {

  private static final IdPrefix CATEGORY_PREFIX = new IdPrefix("cat");
  private static final int MAX_CATEGORIES_PER_DIRECTION = 64;
  private static final ErrorCode INVALID = new ErrorCode("XIAOBU_CATEGORY_INVALID");
  private static final ErrorCode CONFLICT = new ErrorCode("XIAOBU_CATEGORY_CONFLICT");

  private final PersonalLedgerApplicationService ledgers;
  private final LedgerCategoryRepository categories;
  private final EntityIdGenerator ids;

  LedgerCategoryApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerCategoryRepository categories,
      EntityIdGenerator ids) {
    this.ledgers = ledgers;
    this.categories = categories;
    this.ids = ids;
  }

  @Transactional(readOnly = true)
  public List<LedgerCategoryResult> listLedgerCategories(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return catalog(ledgerId).stream().map(LedgerCategoryResult::from).toList();
  }

  @Transactional
  public LedgerCategoryResult createLedgerCategory(CreateLedgerCategoryCommand command) {
    requireManagement(command.ledgerId(), command.actorId());
    String name = command.name() == null ? "" : command.name().strip();
    if (name.isBlank() || name.length() > 24 || command.direction() == null) {
      throw new FoundationException(INVALID, "请输入 1 至 24 个字符的分类名称");
    }
    var rows =
        catalog(command.ledgerId()).stream()
            .filter(row -> row.direction() == command.direction())
            .toList();
    if (rows.stream().anyMatch(row -> row.name().equalsIgnoreCase(name))) {
      throw conflict("已有同名分类，停用的分类可恢复使用");
    }
    if (rows.size() >= MAX_CATEGORIES_PER_DIRECTION) {
      throw conflict("当前方向分类已达上限");
    }
    int order = rows.stream().mapToInt(LedgerCategory::displayOrder).max().orElse(-1) + 1;
    String code =
        "CUS_"
            + UUID.randomUUID().toString().replace("-", "").substring(16).toUpperCase(Locale.ROOT);
    var item =
        new LedgerCategory(
            ids.next(CATEGORY_PREFIX),
            command.ledgerId(),
            code,
            command.direction(),
            name,
            order,
            true);
    categories.save(item);
    return LedgerCategoryResult.from(item);
  }

  @Transactional
  public List<LedgerCategoryResult> changeLedgerCategoryOrder(
      ChangeLedgerCategoryOrderCommand command) {
    requireManagement(command.ledgerId(), command.actorId());
    var active =
        catalog(command.ledgerId()).stream()
            .filter(row -> row.direction() == command.direction() && row.active())
            .toList();
    var available = new HashMap<String, LedgerCategory>();
    active.forEach(row -> available.put(row.code(), row));
    if (command.codes().size() != active.size()
        || !new HashSet<>(command.codes()).equals(available.keySet())) {
      throw conflict("分类目录已变化，请刷新后重试");
    }
    for (int position = 0; position < command.codes().size(); position++) {
      var row = available.get(command.codes().get(position));
      if (row.displayOrder() != position) {
        saveConfigured(row, position, row.active());
      }
    }
    return catalog(command.ledgerId()).stream().map(LedgerCategoryResult::from).toList();
  }

  @Transactional
  public List<LedgerCategoryResult> changeLedgerCategoryStatus(
      ChangeLedgerCategoryStatusCommand command) {
    requireManagement(command.ledgerId(), command.actorId());
    var catalog = catalog(command.ledgerId());
    var selected =
        catalog.stream()
            .filter(row -> row.code().equals(command.code()))
            .findFirst()
            .orElseThrow(() -> new FoundationException(INVALID, "未找到分类"));
    if (!command.active()
        && selected.active()
        && catalog.stream()
                .filter(row -> row.direction() == selected.direction() && row.active())
                .count()
            == 1) {
      throw conflict("每个方向至少保留一个可用分类");
    }
    if (command.active() != selected.active()) {
      // Restored categories go at the end, without renumbering existing active choices.
      int next =
          catalog.stream()
                  .filter(row -> row.direction() == selected.direction())
                  .mapToInt(LedgerCategory::displayOrder)
                  .max()
                  .orElse(0)
              + 1;
      saveConfigured(selected, command.active() ? next : selected.displayOrder(), command.active());
    }
    return catalog(command.ledgerId()).stream().map(LedgerCategoryResult::from).toList();
  }

  void requireActiveCategory(String ledgerId, TransactionDirection direction, String code) {
    if (catalog(ledgerId).stream()
        .noneMatch(
            row -> row.code().equals(code) && row.direction() == direction && row.active())) {
      throw new FoundationException(INVALID, "请选择当前账本启用的同方向分类");
    }
  }

  private List<LedgerCategory> catalog(String ledgerId) {
    var rows = new HashMap<String, LedgerCategory>();
    LedgerCategoryDefaults.forLedger(ledgerId).forEach(row -> rows.put(row.code(), row));
    categories.findByLedger(ledgerId).forEach(row -> rows.put(row.code(), row));
    return rows.values().stream()
        .sorted(
            Comparator.comparing(LedgerCategory::direction)
                .thenComparingInt(LedgerCategory::displayOrder)
                .thenComparing(LedgerCategory::code))
        .toList();
  }

  private void saveConfigured(LedgerCategory row, int order, boolean active) {
    String id = row.id() == null ? ids.next(CATEGORY_PREFIX) : row.id();
    categories.save(
        new LedgerCategory(
            id, row.ledgerId(), row.code(), row.direction(), row.name(), order, active));
  }

  private void requireManagement(String ledgerId, String actorId) {
    ledgers.lockLedger(ledgerId);
    ledgers.requireOwnership(ledgerId, actorId);
  }

  private static FoundationException conflict(String message) {
    return new FoundationException(CONFLICT, ErrorCategory.CONFLICT, message, Map.of());
  }
}
