package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.ChangeLedgerCategoryOrderCommand;
import com.xiaobu.ledger.application.ChangeLedgerCategoryStatusCommand;
import com.xiaobu.ledger.application.CreateLedgerCategoryCommand;
import com.xiaobu.ledger.application.LedgerCategoryApplicationService;
import com.xiaobu.ledger.domain.TransactionDirection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/ledgers/{ledgerId}/categories")
public class LedgerCategoryController {

  private final LedgerCategoryApplicationService categories;
  private final LedgerCategoryWebMapper mapper;

  LedgerCategoryController(
      LedgerCategoryApplicationService categories, LedgerCategoryWebMapper mapper) {
    this.categories = categories;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<List<LedgerCategoryResponse>> listLedgerCategories(
      @PathVariable("ledgerId") String ledgerId, HttpServletRequest request) {
    return ApiResponse.success(
        categories
            .listLedgerCategories(ledgerId, LedgerSessionContext.require(request).actorId())
            .stream()
            .map(mapper::toResponse)
            .toList());
  }

  @PostMapping
  public ApiResponse<LedgerCategoryResponse> createLedgerCategory(
      @PathVariable("ledgerId") String ledgerId,
      @Valid @RequestBody CreateLedgerCategoryRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        mapper.toResponse(
            categories.createLedgerCategory(
                new CreateLedgerCategoryCommand(
                    ledgerId,
                    LedgerSessionContext.require(request).actorId(),
                    TransactionDirection.valueOf(body.direction()),
                    body.name()))));
  }

  @PutMapping("/order")
  public ApiResponse<List<LedgerCategoryResponse>> changeLedgerCategoryOrder(
      @PathVariable("ledgerId") String ledgerId,
      @Valid @RequestBody ChangeLedgerCategoryOrderRequest input,
      HttpServletRequest request) {
    var rows =
        categories.changeLedgerCategoryOrder(
            new ChangeLedgerCategoryOrderCommand(
                ledgerId,
                LedgerSessionContext.require(request).actorId(),
                TransactionDirection.valueOf(input.direction()),
                input.codes()));
    return ApiResponse.success(rows.stream().map(mapper::toResponse).toList());
  }

  @PutMapping("/{code}/status")
  public ApiResponse<List<LedgerCategoryResponse>> changeLedgerCategoryStatus(
      @PathVariable("ledgerId") String ledgerId,
      @PathVariable("code") String code,
      @Valid @RequestBody ChangeLedgerCategoryStatusRequest input,
      HttpServletRequest request) {
    var rows =
        categories.changeLedgerCategoryStatus(
            new ChangeLedgerCategoryStatusCommand(
                ledgerId, LedgerSessionContext.require(request).actorId(), code, input.active()));
    return ApiResponse.success(rows.stream().map(mapper::toResponse).toList());
  }
}
