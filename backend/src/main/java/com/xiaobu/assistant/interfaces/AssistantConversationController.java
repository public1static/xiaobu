package com.xiaobu.assistant.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.assistant.application.AssistantConversationApplicationService;
import com.xiaobu.assistant.application.AssistantConversationApplicationService.Message;
import com.xiaobu.assistant.application.AssistantConversationRepository.Conversation;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.LedgerStatementPageResult;
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

  // 从服务端身份读取私人会话。
  AssistantConversationController(AssistantConversationApplicationService chats) {
    this.chats = chats;
  }

  @PostMapping
  public ApiResponse<Conversation> create(@PathVariable String ledger, HttpServletRequest req) {
    return ApiResponse.success(chats.create(ledger, LedgerSessionContext.require(req).actorId()));
  }

  @GetMapping
  public ApiResponse<List<Conversation>> list(
      @PathVariable String ledger,
      @RequestParam(defaultValue = "0") int offset,
      HttpServletRequest req) {
    return ApiResponse.success(
        chats.list(ledger, LedgerSessionContext.require(req).actorId(), offset));
  }

  @GetMapping("/{id}/messages")
  public ApiResponse<List<Message>> history(
      @PathVariable String ledger,
      @PathVariable String id,
      @RequestParam(defaultValue = "9223372036854775807") long before,
      HttpServletRequest req) {
    return ApiResponse.success(
        chats.history(ledger, LedgerSessionContext.require(req).actorId(), id, before));
  }

  @PostMapping("/{id}/messages")
  public ApiResponse<Message> ask(
      @PathVariable String ledger,
      @PathVariable String id,
      @Valid @RequestBody Ask input,
      HttpServletRequest req) {
    return ApiResponse.success(
        chats.ask(
            ledger,
            LedgerSessionContext.require(req).actorId(),
            id,
            input.question().strip(),
            input.revision(),
            input.clientId().toString(),
            input.replyTo()));
  }

  @GetMapping("/{id}/messages/{sequence}/entries")
  public ApiResponse<LedgerStatementPageResult> entries(
      @PathVariable String ledger,
      @PathVariable String id,
      @PathVariable long sequence,
      @RequestParam(defaultValue = "0") int page,
      HttpServletRequest req) {
    return ApiResponse.success(
        chats.entries(ledger, LedgerSessionContext.require(req).actorId(), id, sequence, page));
  }

  record Ask(
      @NotBlank @Size(max = 500) String question,
      @NotNull @PositiveOrZero Long revision,
      @NotNull UUID clientId,
      @Positive Long replyTo) {}
}
