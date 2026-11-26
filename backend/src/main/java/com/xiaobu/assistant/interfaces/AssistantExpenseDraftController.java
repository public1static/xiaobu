package com.xiaobu.assistant.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.assistant.application.AssistantExpenseDraftApplicationService;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
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

  // 转换草稿 HTTP 字段，保持请求和持久化模型独立。
  private final AssistantWebMapper mapper;

  AssistantExpenseDraftController(
      AssistantExpenseDraftApplicationService drafts, AssistantWebMapper mapper) {
    this.mapper = mapper;
    this.drafts = drafts;
  }

  // 状态查询只返回当前人员在此会话中的草稿。
  @GetMapping
  public ApiResponse<DraftStateResponse> findExpenseDraft(
      @PathVariable String ledger,
      @PathVariable String chat,
      @RequestParam(required = false) String id,
      HttpServletRequest req) {
    return ApiResponse.success(
        new DraftStateResponse(
            mapper.toResponse(
                drafts.findExpenseDraft(
                    ledger, LedgerSessionContext.require(req).actorId(), chat, id))));
  }

  // 缺金额或分类的输入允许保存为待补全状态。
  @PutMapping
  public ApiResponse<DraftResponse> editExpenseDraft(
      @PathVariable String ledger,
      @PathVariable String chat,
      @Valid @RequestBody EditDraftRequest edit,
      HttpServletRequest req) {
    return ApiResponse.success(
        mapper.toResponse(
            drafts.editExpenseDraft(
                ledger,
                LedgerSessionContext.require(req).actorId(),
                chat,
                edit.id(),
                edit.version(),
                edit.revision(),
                edit.question().strip(),
                mapper.toFields(edit.fields()))));
  }

  // 仅显式确认请求可产生流水。
  @PostMapping("/{id}/confirm")
  public ApiResponse<DraftResponse> confirmExpenseDraft(
      @PathVariable String ledger,
      @PathVariable String chat,
      @PathVariable String id,
      @Valid @RequestBody DraftVersionRequest version,
      HttpServletRequest req) {
    return ApiResponse.success(
        mapper.toResponse(
            drafts.confirmExpenseDraft(
                ledger, LedgerSessionContext.require(req).actorId(), chat, id, version.version())));
  }

  // 放弃终止草稿，保留聊天中的处理结果。
  @PostMapping("/{id}/discard")
  public ApiResponse<DraftResponse> discardExpenseDraft(
      @PathVariable String ledger,
      @PathVariable String chat,
      @PathVariable String id,
      @Valid @RequestBody DraftVersionRequest version,
      HttpServletRequest req) {
    return ApiResponse.success(
        mapper.toResponse(
            drafts.discardExpenseDraft(
                ledger, LedgerSessionContext.require(req).actorId(), chat, id, version.version())));
  }

  record DraftStateResponse(DraftResponse draft) {}

  record DraftVersionRequest(@NotNull @PositiveOrZero Long version) {}

  record EditDraftRequest(
      @NotBlank @Size(max = 500) String question,
      @NotNull @PositiveOrZero Long revision,
      String id,
      Long version,
      @NotNull DraftFieldsRequest fields) {}

  record DraftFieldsRequest(
      LocalDate bookedOn, Long amountCents, String categoryCode, String accountId, String note) {}

  record DraftFieldsResponse(
      LocalDate bookedOn, Long amountCents, String categoryCode, String accountId, String note) {}

  record DraftResponse(
      String id, long version, String status, DraftFieldsResponse fields, String transactionId) {}
}
