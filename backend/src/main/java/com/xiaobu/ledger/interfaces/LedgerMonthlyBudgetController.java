package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.LedgerMonthlyBudgetApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/budgets/monthly/{month}")
public class LedgerMonthlyBudgetController {

  private final LedgerMonthlyBudgetApplicationService budgets;
  private final LedgerMonthlyBudgetWebMapper mapper;

  LedgerMonthlyBudgetController(
      LedgerMonthlyBudgetApplicationService budgets, LedgerMonthlyBudgetWebMapper mapper) {
    this.budgets = budgets;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<LedgerMonthlyBudgetResponse> getLedgerMonthlyBudget(
      @PathVariable String ledgerId, @PathVariable String month, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            budgets.getLedgerMonthlyBudget(ledgerId, actor.actorId(), parseMonth(month))));
  }

  @PutMapping
  public ApiResponse<LedgerMonthlyBudgetResponse> setLedgerMonthlyBudget(
      @PathVariable String ledgerId,
      @PathVariable String month,
      @Valid @RequestBody SetLedgerMonthlyBudgetRequest body,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            budgets.setLedgerMonthlyBudget(
                mapper.toCommand(ledgerId, actor.actorId(), parseMonth(month), body))));
  }

  private YearMonth parseMonth(String input) {
    try {
      var month = YearMonth.parse(input);
      if (month.getYear() >= 1900 && month.getYear() <= 9999) {
        return month;
      }
    } catch (DateTimeParseException ignored) {
      // 同样按无效月份处理。
    }
    throw new FoundationException(new ErrorCode("XIAOBU_INVALID_MONTH"), "请选择有效的月份");
  }
}
