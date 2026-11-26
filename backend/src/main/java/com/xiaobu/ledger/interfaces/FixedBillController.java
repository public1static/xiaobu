package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.FixedBillApplicationService;
import com.xiaobu.ledger.application.ListFixedBillCandidatesQuery;
import com.xiaobu.ledger.application.UnlinkFixedBillPaymentCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

  @GetMapping("/plans")
  public ApiResponse<FixedBillPlanPageResponse> listFixedBillPlans(
      @PathVariable String ledgerId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(bills.listFixedBillPlans(ledgerId, actor.actorId(), page)));
  }

  @PutMapping("/plans/{billId}")
  public ApiResponse<FixedBillPeriodResponse> reviseFixedBillPlan(
      @PathVariable String ledgerId,
      @PathVariable String billId,
      @Valid @RequestBody ReviseFixedBillPlanRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            bills.reviseFixedBillPlan(mapper.toCommand(ledgerId, actor.actorId(), billId, body))));
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

  @GetMapping("/{billId}/periods/{month}/payment-candidates")
  public ApiResponse<FixedBillCandidatePageResponse> listFixedBillCandidates(
      @PathVariable String ledgerId,
      @PathVariable String billId,
      @PathVariable @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
      @RequestParam(defaultValue = "") @Size(max = 100) String keyword,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            bills.listFixedBillCandidates(
                new ListFixedBillCandidatesQuery(
                    ledgerId, actor.actorId(), billId, month, keyword, page))));
  }

  @PostMapping("/{billId}/periods/{month}/payment-link")
  public ApiResponse<FixedBillPeriodResponse> linkFixedBillPayment(
      @PathVariable String ledgerId,
      @PathVariable String billId,
      @PathVariable @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
      @Valid @RequestBody LinkFixedBillPaymentRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            bills.linkFixedBillPayment(
                mapper.toCommand(ledgerId, actor.actorId(), billId, month, body))));
  }

  @GetMapping("/payment-references/{transactionId}")
  public ResponseEntity<ApiResponse<FixedBillPaymentReferenceResponse>>
      findFixedBillPaymentReference(
          @PathVariable String ledgerId,
          @PathVariable String transactionId,
          HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return bills
        .findFixedBillPaymentReference(ledgerId, actor.actorId(), transactionId)
        .map(mapper::toResponse)
        .map(response -> ResponseEntity.ok(ApiResponse.success(response)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @DeleteMapping("/payment-references/{transactionId}/{paymentId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void unlinkFixedBillPayment(
      @PathVariable String ledgerId,
      @PathVariable String transactionId,
      @PathVariable String paymentId,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    bills.unlinkFixedBillPayment(
        new UnlinkFixedBillPaymentCommand(ledgerId, actor.actorId(), transactionId, paymentId));
  }
}
