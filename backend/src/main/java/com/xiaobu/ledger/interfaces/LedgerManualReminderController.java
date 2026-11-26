package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.LedgerManualReminderApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/manual-reminders")
class LedgerManualReminderController {
  private final LedgerManualReminderApplicationService reminders;
  private final LedgerManualReminderWebMapper mapper;

  LedgerManualReminderController(
      LedgerManualReminderApplicationService reminders, LedgerManualReminderWebMapper mapper) {
    this.reminders = reminders;
    this.mapper = mapper;
  }

  @GetMapping("/{reminderId}")
  public ApiResponse<LedgerManualReminderResponse> getLedgerManualReminder(
      @PathVariable String ledgerId, @PathVariable String reminderId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            reminders.getLedgerManualReminder(ledgerId, actor.actorId(), reminderId)));
  }

  @PostMapping
  public ApiResponse<LedgerManualReminderResponse> createLedgerManualReminder(
      @PathVariable String ledgerId,
      @Valid @RequestBody SaveLedgerManualReminderRequest body,
      HttpServletRequest request) {
    return saveLedgerManualReminder(ledgerId, null, body, request);
  }

  @PutMapping("/{reminderId}")
  public ApiResponse<LedgerManualReminderResponse> editLedgerManualReminder(
      @PathVariable String ledgerId,
      @PathVariable String reminderId,
      @Valid @RequestBody SaveLedgerManualReminderRequest body,
      HttpServletRequest request) {
    return saveLedgerManualReminder(ledgerId, reminderId, body, request);
  }

  @PutMapping("/{reminderId}/completion")
  public ApiResponse<LedgerManualReminderResponse> setLedgerManualReminderCompletion(
      @PathVariable String ledgerId,
      @PathVariable String reminderId,
      @Valid @RequestBody CompletionRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            reminders.setLedgerManualReminderCompletion(
                ledgerId, actor.actorId(), reminderId, body.completed())));
  }

  private ApiResponse<LedgerManualReminderResponse> saveLedgerManualReminder(
      String ledgerId,
      String reminderId,
      SaveLedgerManualReminderRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            reminders.saveLedgerManualReminder(
                mapper.toCommand(ledgerId, actor.actorId(), reminderId, body))));
  }

  record CompletionRequest(@NotNull Boolean completed) {}
}
