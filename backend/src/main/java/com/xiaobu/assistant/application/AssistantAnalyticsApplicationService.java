package com.xiaobu.assistant.application;

import com.xiaobu.assistant.application.AssistantAnalyticsRepository.Value;
import com.xiaobu.gift.application.GiftDirectoryApplicationService;
import com.xiaobu.ledger.application.LedgerAccountApplicationService;
import com.xiaobu.ledger.application.LedgerCategoryApplicationService;
import com.xiaobu.ledger.application.LedgerMemberApplicationService;
import com.xiaobu.ledger.application.ListLedgerTransactionsQuery;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class AssistantAnalyticsApplicationService {
  private final PersonalLedgerApplicationService ledgers;
  private final LedgerCategoryApplicationService categories;
  private final LedgerAccountApplicationService accounts;
  private final LedgerMemberApplicationService members;
  private final GiftDirectoryApplicationService gifts;
  private final AssistantAnalyticsRepository repository;

  AssistantAnalyticsApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerCategoryApplicationService categories,
      LedgerAccountApplicationService accounts,
      LedgerMemberApplicationService members,
      GiftDirectoryApplicationService gifts,
      AssistantAnalyticsRepository repository) {
    this.ledgers = ledgers;
    this.categories = categories;
    this.accounts = accounts;
    this.members = members;
    this.gifts = gifts;
    this.repository = repository;
  }

  /**
   * 快照沿用业务服务的可见字段与派生余额。
   *
   * @param amounts 派生金额指标，单位为分
   */
  private record Item(Map<String, String> fields, Map<String, Long> amounts) {}

  // 按业务服务的权限和现有字段读取完整快照。
  private List<Item> items(AssistantDataset data, String ledger, String actor) {
    return switch (data) {
      case ACCOUNTS ->
          accounts.listLedgerAccounts(ledger, actor).stream()
              .map(r ->
                  new Item(
                      Map.of("name", r.name()),
                      r.balanceKnown()
                          ? Map.of(
                              "openingBalanceCents",
                              r.openingBalanceCents(),
                              "recordedBalanceCents",
                              r.recordedBalanceCents(),
                              "balanceCents",
                              r.balanceCents())
                          : Map.of("recordedBalanceCents", r.recordedBalanceCents())))
              .toList();
      case CATEGORIES ->
          categories.listLedgerCategories(ledger, actor).stream()
              .map(r ->
                  new Item(
                      Map.of(
                          "name",
                          r.name(),
                          "direction",
                          r.direction().name(),
                          "status",
                          r.active() ? "启用" : "停用"),
                      Map.of()))
              .toList();
      case MEMBERS ->
          members.listLedgerMembers(ledger, actor).stream()
              .map(r -> new Item(Map.of("member", r.memberId(), "role", r.role().name()), Map.of()))
              .toList();
      case GIFT_BOOKS ->
          gifts.listGiftBooks(ledger, actor).stream()
              .map(r ->
                  new Item(
                      Map.of(
                          "name",
                          r.name(),
                          "direction",
                          r.direction().name(),
                          "status",
                          r.archived() ? "归档" : "使用中"),
                      Map.of()))
              .toList();
      case GIFT_CONTACTS ->
          gifts.listGiftContacts(ledger, actor).stream()
              .map(r ->
                  new Item(
                      Map.of(
                          "name", r.name(), "distinction", Objects.toString(r.distinction(), "")),
                      Map.of()))
              .toList();
      default -> throw new IllegalArgumentException("请选择当前目录");
    };
  }

  // 日期必须同时存在，比较复用当前期的所有非日期条件。
  private boolean valid(AssistantDataset.Definition d, AssistantQueryPlan p) {
    var a = p.analysis();
    if (a.metric() == null
        || a.operation() == null
        || !d.operations().contains(a.operation())
        || !d.metrics().contains(a.metric())) {
      return false;
    }
    boolean rank = a.operation().equals("rank"),
        list = a.operation().equals("list"),
        compare = a.operation().equals("compare");
    if (rank ? (a.groupBy() == null || !d.groupBy().contains(a.groupBy())) : a.groupBy() != null) {
      return false;
    }
    if (a.limit() != null && ((!rank && !list) || a.limit() < 1 || a.limit() > 50)) return false;
    if (compare
        ? (a.baselineFrom() == null || a.baselineThrough() == null)
        : (a.baselineFrom() != null || a.baselineThrough() != null)) {
      return false;
    }
    if (!d.dated() && (p.from() != null || p.through() != null || p.recordedByMe())) return false;
    boolean statement = "statement".equals(d.name());
    if (!statement && (p.category() != null || p.account() != null)) return false;
    if (p.direction() != null && !statement && !"giftActivities".equals(d.name())) return false;
    if (a.filters() != null
        && (statement && !a.filters().isEmpty()
            || a.filters().entrySet().stream()
                .anyMatch(e ->
                    !d.groupBy().contains(e.getKey())
                        || e.getValue() == null
                        || e.getValue().isBlank()
                        || e.getValue().length() > 80))) {
      return false;
    }
    return p.keyword() == null || p.keyword().length() <= 80;
  }

  // 权限和整个计划验证通过后，两个期间在同一只读事务内执行。
  @Transactional(readOnly = true)
  public Optional<String> evaluate(
      String ledger, String actor, AssistantQueryPlan p, String category, String account) {
    ledgers.requireReadAccess(ledger, actor);
    var a = p.analysis();
    var data =
        AssistantDataset.findVisible(a.dataset(), ledgers.isOwner(ledger, actor)).orElse(null);
    if (data == null || !valid(data.definition(), p)) return Optional.empty();
    if (data.definition().dated()
        && (!ListLedgerTransactionsQuery.isValidDateRange(p.from(), p.through())
            || ("compare".equals(a.operation())
                && !ListLedgerTransactionsQuery.isValidDateRange(
                    a.baselineFrom(), a.baselineThrough())))) {
      return Optional.empty();
    }
    return Optional.of(describe(ledger, actor, p, category, account, data));
  }

  // 回答只展示已执行的指标、区间和筛选，供后续追问引用。
  private String describe(
      String ledger,
      String actor,
      AssistantQueryPlan p,
      String category,
      String account,
      AssistantDataset data) {
    var def = data.definition();
    var a = p.analysis();
    var conditions = new ArrayList<String>();
    conditions.add(def.description());
    conditions.add(METRICS.get(a.metric()));
    conditions.add(def.dated() ? p.from() + " 至 " + p.through() : "当前快照");
    if (p.category() != null) conditions.add(p.category());
    if (p.account() != null) conditions.add(p.account());
    if (p.direction() != null) conditions.add(p.direction().name());
    if (p.recordedByMe()) conditions.add("我登记的");
    if (p.keyword() != null && !p.keyword().isBlank()) conditions.add("关键词 " + p.keyword());
    if (a.filters() != null) a.filters().forEach((k, v) -> conditions.add(k + "=" + v));
    String title = String.join(" · ", conditions);
    List<Value> rows;
    if (def.dated()) {
      rows = repository.query(ledger, actor, p, category, account, p.from(), p.through());
    } else {
      var snapshot = snapshot(data, ledger, actor, p);
      rows = snapshot.rows();
      if (snapshot.unknownCount() > 0) {
        if (rows.isEmpty() || rows.getFirst().count() == 0) {
          return title + "\n匹配的 " + snapshot.unknownCount() + " 个账户余额均待填。";
        }
        title += "（仅统计已知金额，另有 " + snapshot.unknownCount() + " 个账户余额待填）";
      }
    }
    if ("compare".equals(a.operation())) {
      var base =
          repository
              .query(ledger, actor, p, category, account, a.baselineFrom(), a.baselineThrough())
              .getFirst();
      var current = rows.getFirst();
      return title
          + "\n本期 "
          + amount(current, a.metric())
          + "，基期 "
          + a.baselineFrom()
          + " 至 "
          + a.baselineThrough()
          + " 为 "
          + amount(base, a.metric())
          + "，差额（本期减基期）"
          + display(current.value().subtract(base.value()), a.metric());
    }
    if (rows.isEmpty() || rows.getFirst().count() == 0) return title + "\n没有匹配记录。";
    if ("total".equals(a.operation())) return title + "\n" + amount(rows.getFirst(), a.metric());
    Map<String, String> labels = new HashMap<>();
    if (data == AssistantDataset.STATEMENT && "CATEGORY".equals(a.groupBy())) {
      categories.listLedgerCategories(ledger, actor).forEach(c -> labels.put(c.code(), c.name()));
    }
    if (data == AssistantDataset.STATEMENT && "ACCOUNT".equals(a.groupBy())) {
      accounts.listLedgerAccounts(ledger, actor).forEach(c -> labels.put(c.id(), c.name()));
    }
    var lines = new ArrayList<String>();
    lines.add(title + "（最多 " + (a.limit() == null ? 10 : a.limit()) + " 项）");
    for (var row : rows) {
      var label = Objects.toString(row.label(), "未指定");
      lines.add(labels.getOrDefault(label, label) + " · " + display(row.value(), a.metric()));
    }
    return String.join("\n", lines);
  }

  // 空期间单独标明，金额统一以元展示。
  private String amount(Value row, String metric) {
    return display(row.value(), metric) + (row.count() == 0 ? "（无记录）" : "");
  }

  // 只在输出层把分换算为元。
  private String display(BigDecimal value, String metric) {
    return metric.endsWith("Cents")
        ? value.movePointLeft(2).setScale(2).toPlainString() + " 元"
        : value.toPlainString() + " 条";
  }

  // 面向用户的指标名称与金额单位相互独立。
  private static final Map<String, String> METRICS =
      Map.of(
          "incomeCents",
          "收入",
          "grossExpenseCents",
          "原支出",
          "refundCents",
          "退款",
          "netExpenseCents",
          "净支出",
          "expenseCents",
          "支出",
          "entryCount",
          "数量",
          "amountCents",
          "发生额",
          "balanceCents",
          "当前余额",
          "recordedBalanceCents",
          "流水净额",
          "openingBalanceCents",
          "期初余额");

  private record Snapshot(List<Value> rows, long unknownCount) {}

  // 当前目录含业务派生值，按完整目录分组后再截取排行。
  private Snapshot snapshot(
      AssistantDataset data, String ledger, String actor, AssistantQueryPlan p) {
    var a = p.analysis();
    var grouped = new TreeMap<String, BigDecimal>();
    var rows = new ArrayList<Value>();
    long count = 0;
    BigDecimal sum = BigDecimal.ZERO;
    long unknownCount = 0;
    for (var item : items(data, ledger, actor)) {
      if (a.filters() != null && !item.fields().entrySet().containsAll(a.filters().entrySet())) {
        continue;
      }
      var label =
          String.join(
              " · ",
              data.definition().groupBy().stream()
                  .map(item.fields()::get)
                  .filter(s -> !s.isEmpty())
                  .toList());
      if (p.keyword() != null && !label.contains(p.keyword())) {
        continue;
      }
      // 待填余额仍计入账户数，只对已知金额求和或排行。
      if (data == AssistantDataset.ACCOUNTS
          && !"entryCount".equals(a.metric())
          && !item.amounts().containsKey(a.metric())) {
        unknownCount++;
        continue;
      }
      var value = BigDecimal.valueOf(item.amounts().getOrDefault(a.metric(), 1L));
      count++;
      sum = sum.add(value);
      if ("list".equals(a.operation())) {
        rows.add(new Value(label, value, 1));
      } else if ("rank".equals(a.operation())) {
        grouped.merge(item.fields().get(a.groupBy()), value, BigDecimal::add);
      }
    }
    if ("total".equals(a.operation())) {
      return new Snapshot(List.of(new Value("", sum, count)), unknownCount);
    }
    if ("rank".equals(a.operation())) {
      grouped.forEach((k, v) -> rows.add(new Value(k, v, 1)));
      rows.sort(Comparator.comparing(Value::value).reversed().thenComparing(Value::label));
    }
    return new Snapshot(
        rows.stream().limit(a.limit() == null ? 10 : a.limit()).toList(), unknownCount);
  }
}
