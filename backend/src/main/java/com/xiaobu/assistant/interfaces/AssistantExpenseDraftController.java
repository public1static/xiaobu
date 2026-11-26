package com.xiaobu.assistant.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.assistant.application.AssistantExpenseDraftApplicationService;
import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Draft;
import com.xiaobu.assistant.application.AssistantExpenseDraftRepository.Fields;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledger}/assistant/conversations/{chat}/drafts")
public class AssistantExpenseDraftController {
  private final AssistantExpenseDraftApplicationService drafts;

  AssistantExpenseDraftController(AssistantExpenseDraftApplicationService drafts) {
    this.drafts = drafts;
  }

  // 状态查询只返回当前人员在此会话中的草稿。
  @GetMapping
  public ApiResponse<State> read(
      @PathVariable String ledger,
      @PathVariable String chat,
      @RequestParam(required = false) String id,
      HttpServletRequest req) {
    return ApiResponse.success(
        new State(drafts.read(ledger, LedgerSessionContext.require(req).actorId(), chat, id)));
  }

  // 缺金额或分类的输入允许保存为待补全状态。
  @PutMapping
  public ApiResponse<Draft> edit(
      @PathVariable String ledger,
      @PathVariable String chat,
      @Valid @RequestBody Edit edit,
      HttpServletRequest req) {
    return ApiResponse.success(
        drafts.edit(
            ledger,
            LedgerSessionContext.require(req).actorId(),
            chat,
            edit.id(),
            edit.version(),
            edit.revision(),
            edit.question().strip(),
            edit.fields()));
  }

  // 仅显式确认请求可产生流水。
  @PostMapping("/{id}/confirm")
  public ApiResponse<Draft> confirm(
      @PathVariable String ledger,
      @PathVariable String chat,
      @PathVariable String id,
      @Valid @RequestBody Version v,
      HttpServletRequest req) {
    return ApiResponse.success(
        drafts.finish(
            ledger, LedgerSessionContext.require(req).actorId(), chat, id, v.version(), true));
  }

  // 放弃终止草稿，保留聊天中的处理结果。
  @PostMapping("/{id}/discard")
  public ApiResponse<Draft> discard(
      @PathVariable String ledger,
      @PathVariable String chat,
      @PathVariable String id,
      @Valid @RequestBody Version v,
      HttpServletRequest req) {
    return ApiResponse.success(
        drafts.finish(
            ledger, LedgerSessionContext.require(req).actorId(), chat, id, v.version(), false));
  }

  record State(Draft draft) {}

  record Version(@NotNull @PositiveOrZero Long version) {}

  record Edit(
      @NotBlank @Size(max = 500) String question,
      @NotNull @PositiveOrZero Long revision,
      String id,
      Long version,
      @NotNull Fields fields) {}
}
