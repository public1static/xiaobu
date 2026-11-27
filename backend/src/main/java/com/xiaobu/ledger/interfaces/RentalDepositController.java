package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.RentalDepositApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/rentals/contracts/{contractId}/deposits")
class RentalDepositController {
  private final RentalDepositApplicationService deposits;
  private final RentalDepositWebMapper mapper;

  RentalDepositController(RentalDepositApplicationService deposits, RentalDepositWebMapper mapper) {
    this.deposits = deposits;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<RentalDepositPageResponse> listRentalDeposits(
      @PathVariable String ledgerId,
      @PathVariable String contractId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            deposits.listRentalDeposits(ledgerId, actor.actorId(), contractId, page)));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void recordRentalDeposit(
      @PathVariable String ledgerId,
      @PathVariable String contractId,
      @Valid @RequestBody RecordRentalDepositRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    deposits.recordRentalDeposit(mapper.toCommand(ledgerId, actor.actorId(), contractId, body));
  }

  @PutMapping("/{depositId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void editRentalDeposit(
      @PathVariable String ledgerId,
      @PathVariable String contractId,
      @PathVariable String depositId,
      @Valid @RequestBody EditRentalDepositRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    deposits.editRentalDeposit(
        mapper.toCommand(ledgerId, actor.actorId(), contractId, depositId, body));
  }
}
