package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.LedgerTodoApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/todos")
class LedgerTodoController {

  private final LedgerTodoApplicationService todos;
  private final LedgerTodoWebMapper mapper;

  LedgerTodoController(LedgerTodoApplicationService todos, LedgerTodoWebMapper mapper) {
    this.todos = todos;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<LedgerTodoPageResponse> listLedgerTodos(
      @PathVariable String ledgerId,
      @RequestParam(defaultValue = "false") boolean completed,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(todos.listLedgerTodos(ledgerId, actor.actorId(), completed, page)));
  }

  @GetMapping("/count")
  public ApiResponse<LedgerTodoCountResponse> countPendingLedgerTodos(
      @PathVariable String ledgerId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        new LedgerTodoCountResponse(todos.countPendingLedgerTodos(ledgerId, actor.actorId())));
  }
}
