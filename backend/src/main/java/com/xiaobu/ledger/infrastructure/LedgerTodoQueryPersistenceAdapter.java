package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerTodoPageResult;
import com.xiaobu.ledger.application.LedgerTodoQueryRepository;
import com.xiaobu.ledger.application.LedgerTodoResult;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class LedgerTodoQueryPersistenceAdapter implements LedgerTodoQueryRepository {

  private static final int PAGE_SIZE = 20;
  private final NamedParameterJdbcTemplate jdbc;
  private final String openItems;
  private final String completedItems;

  LedgerTodoQueryPersistenceAdapter(
      NamedParameterJdbcTemplate jdbc, List<LedgerTodoSource> sources) {
    this.jdbc = jdbc;
    openItems =
        sources.stream()
            .map(LedgerTodoSource::openItemsSql)
            .collect(Collectors.joining(" UNION ALL "));
    completedItems =
        sources.stream()
            .map(LedgerTodoSource::completedItemsSql)
            .collect(Collectors.joining(" UNION ALL "));
  }

  @Override
  public LedgerTodoPageResult listLedgerTodos(
      String ledgerId, LocalDate today, boolean completed, int page) {
    var parameters = parameters(ledgerId, today, false);
    String from = " FROM (" + (completed ? completedItems : openItems) + ") items";
    var counts =
        jdbc.queryForObject(
            "SELECT COUNT(*) AS total, SUM(todo_status = 'PENDING') AS pending" + from,
            parameters,
            (row, index) -> new Counts(row.getLong("total"), row.getLong("pending")));
    long total = counts.total();
    long pending = completed ? countPendingLedgerTodos(ledgerId, today) : counts.pending();
    int offset = page * PAGE_SIZE;
    if (offset >= total)
      return new LedgerTodoPageResult(List.of(), total, pending, page, PAGE_SIZE, today);

    String order =
        completed
            ? " ORDER BY due_on DESC, source, source_id, occurrence"
            : " ORDER BY (todo_status = 'PENDING') DESC, due_on, source, source_id, occurrence";
    parameters.addValue("limit", PAGE_SIZE).addValue("offset", offset);
    var entries =
        jdbc.query(
            "SELECT *" + from + order + " LIMIT :limit OFFSET :offset",
            parameters,
            (row, index) ->
                new LedgerTodoResult(
                    row.getString("source"),
                    row.getString("source_id"),
                    row.getString("occurrence"),
                    row.getObject("due_on", LocalDate.class),
                    row.getString("todo_status"),
                    row.getString("title"),
                    row.getString("subtitle"),
                    row.getObject("amount_cents", Long.class)));
    return new LedgerTodoPageResult(entries, total, pending, page, PAGE_SIZE, today);
  }

  @Override
  public long countPendingLedgerTodos(String ledgerId, LocalDate today) {
    return count(
        "SELECT COUNT(*) FROM (" + openItems + ") items WHERE todo_status = 'PENDING'",
        parameters(ledgerId, today, true));
  }

  private record Counts(long total, long pending) {}

  private long count(String sql, MapSqlParameterSource parameters) {
    return jdbc.queryForObject(sql, parameters, Long.class);
  }

  private MapSqlParameterSource parameters(String ledgerId, LocalDate today, boolean pendingOnly) {
    return new MapSqlParameterSource("ledgerId", ledgerId)
        .addValue("today", today)
        .addValue("pendingOnly", pendingOnly ? 1 : 0);
  }
}
