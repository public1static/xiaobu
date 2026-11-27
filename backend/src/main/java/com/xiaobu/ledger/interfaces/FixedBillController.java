package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.FixedBillApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.YearMonth;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/fixed-bills")
class FixedBillController {
  private final FixedBillApplicationService bills;
  private final FixedBillWebMapper mapper;

  FixedBillController(FixedBillApplicationService bills, FixedBillWebMapper mapper) {
    this.bills = bills;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<FixedBillPageResponse> listFixedBillPeriods(
      @PathVariable String ledgerId,
      @RequestParam @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
      @RequestParam(defaultValue = "false") boolean paid,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            bills.listFixedBillPeriods(ledgerId, actor.actorId(), month, paid, page)));
  }

  @PostMapping
  public ApiResponse<FixedBillPeriodResponse> createFixedBill(
      @PathVariable String ledgerId,
      @Valid @RequestBody CreateFixedBillRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            bills.createFixedBill(mapper.toCommand(ledgerId, actor.actorId(), body))));
  }

  @PostMapping("/{billId}/periods/{month}/payment")
  public ApiResponse<FixedBillPeriodResponse> payFixedBill(
      @PathVariable String ledgerId,
      @PathVariable String billId,
      @PathVariable @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
      @Valid @RequestBody PayFixedBillRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            bills.payFixedBill(mapper.toCommand(ledgerId, actor.actorId(), billId, month, body))));
  }
}
