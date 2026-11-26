package com.xiaobu.assistant.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.assistant.application.AssistantConversationApplicationService;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.interfaces.LedgerStatementPageResponse;
import com.xiaobu.ledger.interfaces.LedgerTransactionWebMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledger}/assistant/conversations")
public class AssistantConversationController {
  private final AssistantConversationApplicationService chats;

  // 将聊天和流水分别转换为对应接口的响应。
  private final AssistantWebMapper mapper;
  private final LedgerTransactionWebMapper statements;

  // 从服务端身份读取私人会话。
  AssistantConversationController(
      AssistantConversationApplicationService chats,
      AssistantWebMapper mapper,
      LedgerTransactionWebMapper statements) {
    this.mapper = mapper;
    this.statements = statements;
    this.chats = chats;
  }

  // 新会话归属当前账本及登录人。
  @PostMapping
  public ApiResponse<ConversationResponse> createConversation(
      @PathVariable String ledger, HttpServletRequest req) {
    return ApiResponse.success(
        mapper.toResponse(
            chats.createConversation(ledger, LedgerSessionContext.require(req).actorId())));
  }

  // 按更新时间分页列出当前用户的会话。
  @GetMapping
  public ApiResponse<List<ConversationResponse>> listConversations(
      @PathVariable String ledger,
      @RequestParam(defaultValue = "0") int offset,
      HttpServletRequest req) {
    return ApiResponse.success(
        mapper.toConversationResponses(
            chats.listConversations(ledger, LedgerSessionContext.require(req).actorId(), offset)));
  }

  // 读取游标之前的一页历史。
  @GetMapping("/{id}/messages")
  public ApiResponse<List<AssistantMessageResponse>> listMessages(
      @PathVariable String ledger,
      @PathVariable String id,
      @RequestParam(defaultValue = "9223372036854775807") long before,
      HttpServletRequest req) {
    return ApiResponse.success(
        mapper.toMessageResponses(
            chats.listMessages(ledger, LedgerSessionContext.require(req).actorId(), id, before)));
  }

  // 按客户端请求编号提交一轮提问。
  @PostMapping("/{id}/messages")
  public ApiResponse<AssistantMessageResponse> askQuestion(
      @PathVariable String ledger,
      @PathVariable String id,
      @Valid @RequestBody AskAssistantRequest input,
      HttpServletRequest req) {
    return ApiResponse.success(
        mapper.toResponse(
            chats.askQuestion(
                ledger,
                LedgerSessionContext.require(req).actorId(),
                id,
                input.question().strip(),
                input.revision(),
                input.clientId().toString(),
                input.replyTo())));
  }

  // 按所选回答的来源条件查询流水。
  @GetMapping("/{id}/messages/{sequence}/entries")
  public ApiResponse<LedgerStatementPageResponse> listMessageEntries(
      @PathVariable String ledger,
      @PathVariable String id,
      @PathVariable long sequence,
      @RequestParam(defaultValue = "0") int page,
      HttpServletRequest req) {
    return ApiResponse.success(
        statements.toResponse(
            chats.listMessageEntries(
                ledger, LedgerSessionContext.require(req).actorId(), id, sequence, page)));
  }

  record ConversationResponse(String id, String title, long revision) {}

  record AskAssistantRequest(
      @NotBlank @Size(max = 500) String question,
      @NotNull @PositiveOrZero Long revision,
      @NotNull UUID clientId,
      @Positive Long replyTo) {}
}
