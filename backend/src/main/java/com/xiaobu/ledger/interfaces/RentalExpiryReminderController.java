package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.RentalExpiryReminderApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/rentals")
class RentalExpiryReminderController {
  private final RentalExpiryReminderApplicationService reminders;
  private final RentalExpiryReminderWebMapper mapper;
  private final RentalWebMapper rentalMapper;

  RentalExpiryReminderController(
      RentalExpiryReminderApplicationService reminders,
      RentalExpiryReminderWebMapper mapper,
      RentalWebMapper rentalMapper) {
    this.reminders = reminders;
    this.mapper = mapper;
    this.rentalMapper = rentalMapper;
  }

  @GetMapping("/expiry-reminders")
  public ApiResponse<RentalExpiryReminderPageResponse> listRentalExpiryReminders(
      @PathVariable String ledgerId,
      @RequestParam(defaultValue = "false") boolean completed,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            reminders.listRentalExpiryReminders(ledgerId, actor.actorId(), completed, page)));
  }

  @GetMapping("/expiry-reminders/count")
  public ApiResponse<RentalExpiryReminderCountResponse> countRentalExpiryReminders(
      @PathVariable String ledgerId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        new RentalExpiryReminderCountResponse(
            reminders.countRentalExpiryReminders(ledgerId, actor.actorId())));
  }

  @PutMapping("/contracts/{contractId}/expiry-reminder")
  public ApiResponse<RentalContractResponse> configureRentalExpiryReminder(
      @PathVariable String ledgerId,
      @PathVariable String contractId,
      @Valid @RequestBody ConfigureRentalExpiryReminderRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        rentalMapper.toResponse(
            reminders.configureRentalExpiryReminder(
                mapper.toCommand(ledgerId, actor.actorId(), contractId, body))));
  }

  @PutMapping("/contracts/{contractId}/expiry-reminder/complete")
  public ApiResponse<RentalContractResponse> completeRentalExpiryReminder(
      @PathVariable String ledgerId,
      @PathVariable String contractId,
      @Valid @RequestBody CompleteRentalExpiryReminderRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        rentalMapper.toResponse(
            reminders.completeRentalExpiryReminder(
                mapper.toCommand(ledgerId, actor.actorId(), contractId, body))));
  }
}
