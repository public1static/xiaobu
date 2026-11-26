package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.ListRentalBillPlansQuery;
import com.xiaobu.ledger.application.RentalApplicationService;
import com.xiaobu.ledger.application.RentalPageResult;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Function;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
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
@RequestMapping("/api/v1/ledgers/{ledgerId}/rentals")
class RentalController {
  private final RentalApplicationService rentals;
  private final RentalWebMapper mapper;

  private final FixedBillWebMapper billMapper;

  RentalController(
      RentalApplicationService rentals, RentalWebMapper mapper, FixedBillWebMapper billMapper) {
    this.rentals = rentals;
    this.mapper = mapper;
    this.billMapper = billMapper;
  }

  @GetMapping("/homes")
  public ApiResponse<RentalPageResponse<RentalHomeResponse>> listRentalHomes(
      @PathVariable String ledgerId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        toPage(rentals.listRentalHomes(ledgerId, actor.actorId(), page), mapper::toResponse));
  }

  @PostMapping("/homes")
  public ApiResponse<RentalHomeResponse> createRentalHome(
      @PathVariable String ledgerId,
      @Valid @RequestBody SaveRentalHomeRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            rentals.saveRentalHome(mapper.toCommand(ledgerId, actor.actorId(), null, body))));
  }

  @PutMapping("/homes/{homeId}")
  public ApiResponse<RentalHomeResponse> updateRentalHome(
      @PathVariable String ledgerId,
      @PathVariable String homeId,
      @Valid @RequestBody SaveRentalHomeRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            rentals.saveRentalHome(mapper.toCommand(ledgerId, actor.actorId(), homeId, body))));
  }

  @GetMapping("/contracts")
  public ApiResponse<RentalPageResponse<RentalContractResponse>> listRentalContracts(
      @PathVariable String ledgerId,
      @RequestParam(required = false) @Size(max = 48) String homeId,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        toPage(
            rentals.listRentalContracts(ledgerId, actor.actorId(), homeId, page),
            mapper::toResponse));
  }

  @GetMapping("/contracts/{contractId}")
  public ApiResponse<RentalContractResponse> getRentalContract(
      @PathVariable String ledgerId, @PathVariable String contractId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(rentals.getRentalContract(ledgerId, actor.actorId(), contractId)));
  }

  @PostMapping("/contracts/{contractId}/renewals")
  public ApiResponse<RentalContractResponse> renewRentalContract(
      @PathVariable String ledgerId,
      @PathVariable String contractId,
      @Valid @RequestBody RenewRentalContractRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            rentals.renewRentalContract(
                mapper.toCommand(ledgerId, actor.actorId(), contractId, body))));
  }

  @PostMapping("/contracts")
  public ApiResponse<RentalContractResponse> createRentalContract(
      @PathVariable String ledgerId,
      @Valid @RequestBody CreateRentalContractRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            rentals.createRentalContract(mapper.toCommand(ledgerId, actor.actorId(), body))));
  }

  @PutMapping("/contracts/{contractId}/bill")
  public ApiResponse<RentalContractResponse> generateRentalBill(
      @PathVariable String ledgerId,
      @PathVariable String contractId,
      @Valid @RequestBody GenerateRentalBillRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            rentals.generateRentalBill(
                mapper.toCommand(ledgerId, actor.actorId(), contractId, body))));
  }

  @GetMapping("/payment-periods")
  public ApiResponse<FixedBillPageResponse> listPendingRentalPeriods(
      @PathVariable String ledgerId,
      @RequestParam @DateTimeFormat(pattern = "uuuu-MM") YearMonth month,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        billMapper.toResponse(
            rentals.listPendingRentalPeriods(ledgerId, actor.actorId(), month, page)));
  }

  @GetMapping("/bill-candidates")
  public ApiResponse<RentalPageResponse<RentalBillPlanResponse>> listRentalBillPlans(
      @PathVariable String ledgerId,
      @RequestParam RentalPaymentCycle cycle,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startsOn,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endsOn,
      @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        toPage(
            rentals.listRentalBillPlans(
                new ListRentalBillPlansQuery(
                    ledgerId, actor.actorId(), cycle, startsOn, endsOn, page)),
            mapper::toResponse));
  }

  @PutMapping("/contracts/{contractId}/bill-link")
  public ApiResponse<RentalContractResponse> linkRentalBill(
      @PathVariable String ledgerId,
      @PathVariable String contractId,
      @Valid @RequestBody LinkRentalBillRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            rentals.linkRentalBill(mapper.toCommand(ledgerId, actor.actorId(), contractId, body))));
  }

  private static <T, R> RentalPageResponse<R> toPage(RentalPageResult<T> page, Function<T, R> map) {
    return new RentalPageResponse<>(
        page.entries().stream().map(map).toList(), page.totalCount(), page.page(), page.pageSize());
  }
}
