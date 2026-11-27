package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class LedgerTodoApplicationService {

  private static final ZoneId LEDGER_ZONE = ZoneId.of("Asia/Shanghai");
  private final PersonalLedgerApplicationService ledgers;
  private final LedgerTodoQueryRepository todos;

  LedgerTodoApplicationService(
      PersonalLedgerApplicationService ledgers, LedgerTodoQueryRepository todos) {
    this.ledgers = ledgers;
    this.todos = todos;
  }

  @Transactional(readOnly = true)
  public LedgerTodoPageResult listLedgerTodos(
      String ledgerId, String actorId, boolean completed, int page) {
    ledgers.requireReadAccess(ledgerId, actorId);
    if (page < 0 || page > 1_000_000) {
      throw new FoundationException(new ErrorCode("XIAOBU_INVALID_PAGE"), "请选择有效的分页参数");
    }
    return todos.listLedgerTodos(
        new LedgerTodoQuery(ledgerId, actorId, LocalDateTime.now(LEDGER_ZONE)), completed, page);
  }

  @Transactional(readOnly = true)
  public long countPendingLedgerTodos(String ledgerId, String actorId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return todos.countPendingLedgerTodos(
        new LedgerTodoQuery(ledgerId, actorId, LocalDateTime.now(LEDGER_ZONE)));
  }
}
