package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.ledger.application.ExpenseApplicationService;
import com.xiaobu.ledger.application.ListExpensesQuery;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** First vertical slice, available only with an explicit local preview profile. */
@RestController
@Profile("local-expense")
@RequestMapping("/api/v1/expenses")
public class ExpenseController {

  private static final String PREVIEW_LEDGER_ID = "book_local_primary";
  private static final String PREVIEW_ACTOR_ID = "user_local_primary";
  private static final ErrorCode INVALID_MONTH = new ErrorCode("XIAOBU_INVALID_MONTH");

  private final ExpenseApplicationService expenses;
  private final ExpenseWebMapper mapper;

  ExpenseController(ExpenseApplicationService expenses, ExpenseWebMapper mapper) {
    this.expenses = expenses;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<List<ExpenseResponse>> listExpenses(@RequestParam("month") String month) {
    var query = new ListExpensesQuery(PREVIEW_LEDGER_ID, PREVIEW_ACTOR_ID, parseMonth(month));
    return ApiResponse.success(
        expenses.listExpenses(query).stream().map(mapper::toResponse).toList());
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<ExpenseResponse> recordExpense(
      @Valid @RequestBody RecordExpenseRequest request) {
    var command = mapper.toCommand(request, PREVIEW_LEDGER_ID, PREVIEW_ACTOR_ID);
    return ApiResponse.success(mapper.toResponse(expenses.recordExpense(command)));
  }

  private static YearMonth parseMonth(String value) {
    try {
      YearMonth month = YearMonth.parse(value);
      if (month.getYear() >= 1900 && month.getYear() <= 9999) {
        return month;
      }
    } catch (DateTimeParseException e) {
      throw new FoundationException(INVALID_MONTH, "月份格式为 YYYY-MM");
    }
    throw new FoundationException(INVALID_MONTH, "月份超出有效范围");
  }
}
