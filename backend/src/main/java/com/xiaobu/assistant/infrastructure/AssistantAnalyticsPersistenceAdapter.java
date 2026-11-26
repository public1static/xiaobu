package com.xiaobu.assistant.infrastructure;

import com.xiaobu.assistant.application.AssistantAnalyticsRepository;
import com.xiaobu.assistant.application.AssistantQueryPlan;
import com.xiaobu.ledger.infrastructure.LedgerStatementSql;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AssistantAnalyticsPersistenceAdapter implements AssistantAnalyticsRepository {
  /**
   * 所有字段表达式由服务端定义，用户提供的筛选值一律绑定。
   *
   * @param dimensions 可供筛选或分组的逻辑名称与列表达式
   */
  private record Projection(
      String from,
      Map<String, String> dimensions,
      Map<String, String> amounts,
      String detail,
      String date,
      String id) {}

  // 共用流水与退款的日期、归属和删除过滤。
  private static final Projection STATEMENT =
      new Projection(
          LedgerStatementSql.STATEMENT,
          Map.of(
              "CATEGORY",
              "category_code",
              "ACCOUNT",
              "account_id",
              "RECORDER",
              "recorder",
              "DAY",
              "booked_on",
              "WEEK",
              "date_sub(booked_on, interval weekday(booked_on) day)",
              "MONTH",
              "date_format(booked_on,'%Y-%m')"),
          Map.of(
              "incomeCents",
              "if(direction='INCOME',amount_cents,0)",
              "netExpenseCents",
              "if(kind=1,-amount_cents,if(direction='EXPENSE',amount_cents,0))",
              "grossExpenseCents",
              "if(kind=0 and direction='EXPENSE',amount_cents,0)",
              "refundCents",
              "if(kind=1,amount_cents,0)"),
          "note",
          "booked_on",
          "id");
  // 互转从源和目标两个账户分别取得可解释的名称。
  private static final Projection TRANSFERS =
      new Projection(
          """
          from ledger_account_transfers t join ledger_accounts s on s.ledger_id=t.ledger_id and s.id=t.source_account_id
          join ledger_accounts d on d.ledger_id=t.ledger_id and d.id=t.target_account_id
          where t.ledger_id=:ledger and t.booked_on between :from and :through
          and (:keyword='' or locate(lower(:keyword),lower(t.note))>0)
          and (:recorder is null or t.created_by=:recorder)
          """,
          Map.of(
              "source",
              "s.name",
              "target",
              "d.name",
              "day",
              "t.booked_on",
              "month",
              "date_format(t.booked_on,'%Y-%m')"),
          Map.of("amountCents", "t.amount_cents"),
          "concat(s.name,' → ',d.name,' ',t.note)",
          "t.booked_on",
          "t.id");
  // 礼金按原单统计，兼容没有自定义礼簿的历史记录。
  private static final Projection GIFTS =
      new Projection(
          """
          from ledger_gift_details g join ledger_transactions t on t.ledger_id=g.ledger_id and t.id=g.transaction_id
          left join gift_books b on b.ledger_id=g.ledger_id and b.id=g.book_id
          where g.ledger_id=:ledger and g.deleted=0 and t.deleted=0 and t.booked_on between :from and :through
          and (:direction is null or t.direction=:direction) and (:recorder is null or t.created_by=:recorder)
          and (:keyword='' or
               locate(lower(:keyword),lower(concat_ws(' ',g.person_name,g.person_distinction,g.reason,t.note)))>0)
          """,
          Map.of(
              "person",
              "concat_ws(' · ',g.person_name,nullif(g.person_distinction,''))",
              "book",
              "coalesce(b.name,if(g.book_code='DAILY_SENT','日常送礼','日常收礼'))",
              "direction",
              "t.direction",
              "day",
              "t.booked_on",
              "month",
              "date_format(t.booked_on,'%Y-%m')"),
          Map.of(
              "incomeCents",
              "if(t.direction='INCOME',t.amount_cents,0)",
              "expenseCents",
              "if(t.direction='EXPENSE',t.amount_cents,0)",
              "amountCents",
              "t.amount_cents"),
          "concat_ws(' · ',g.person_name,g.person_distinction,g.reason,t.note)",
          "t.booked_on",
          "t.id");
  // 绑定本轮账本、日期及筛选，复用业务事务连接。
  private final JdbcClient jdbc;

  AssistantAnalyticsPersistenceAdapter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  // 比较的两个区间复用同一投影与条件，互转不计入收支。
  public List<Value> query(
      String ledger,
      String actor,
      AssistantQueryPlan p,
      String categoryCode,
      String accountId,
      LocalDate from,
      LocalDate through) {
    var a = p.analysis();
    var src =
        switch (a.dataset()) {
          case "statement" -> STATEMENT;
          case "transfers" -> TRANSFERS;
          case "giftActivities" -> GIFTS;
          default -> throw new IllegalArgumentException("不支持的期间数据");
        };
    Map<String, Object> params = new HashMap<>();
    params.put("ledger", ledger);
    params.put("from", from);
    params.put("through", through);
    params.put("keyword", p.keyword() == null ? "" : p.keyword());
    params.put("direction", p.direction() == null ? null : p.direction().name());
    params.put("recorder", p.recordedByMe() ? actor : null);
    params.put("category", categoryCode);
    params.put("account", accountId);
    params.put("unassigned", false);
    String where = src.from();
    if (a.filters() != null)
      for (var entry : a.filters().entrySet()) {
        String param = "f" + params.size();
        where += " and " + src.dimensions().get(entry.getKey()) + "=:" + param;
        params.put(param, entry.getValue());
      }
    boolean rank = "rank".equals(a.operation()), list = "list".equals(a.operation());
    String measure = "entryCount".equals(a.metric()) ? "1" : src.amounts().get(a.metric());
    String label = rank ? src.dimensions().get(a.groupBy()) : "''";
    String sql =
        list
            ? "select concat("
                + src.date()
                + ",' · ',"
                + src.detail()
                + ") label,"
                + measure
                + " val,1 n "
                + where
                + " order by "
                + src.date()
                + " desc,"
                + src.id()
                + " desc limit :limit"
            : "select "
                + label
                + " label,coalesce(sum("
                + measure
                + "),0) val,count(*) n "
                + where
                + (rank ? " group by label order by val desc,label asc limit :limit" : "");
    params.put("limit", a.limit() == null ? 10 : a.limit());
    return jdbc.sql(sql)
        .params(params)
        .query(
            (rs, i) -> new Value(rs.getString("label"), rs.getBigDecimal("val"), rs.getLong("n")))
        .list();
  }
}
