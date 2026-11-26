package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LocalSessionContext;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.ListLedgerTransactionsQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense")
@RequestMapping("/api/v1/ledgers/{ledgerId}/transactions")
public class LedgerTransactionController {

  private static final ErrorCode INVALID_MONTH = new ErrorCode("XIAOBU_INVALID_MONTH");

  private final LedgerTransactionApplicationService transactions;
  private final LedgerTransactionWebMapper mapper;

  LedgerTransactionController(
      LedgerTransactionApplicationService transactions, LedgerTransactionWebMapper mapper) {
    this.transactions = transactions;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<List<LedgerTransactionResponse>> listLedgerTransactions(
      @PathVariable("ledgerId") String ledgerId,
      @RequestParam("month") String month,
      HttpServletRequest request) {
    var actor = LocalSessionContext.require(request);
    var query = new ListLedgerTransactionsQuery(ledgerId, actor.actorId(), parseMonth(month));
    return ApiResponse.success(
        transactions.listLedgerTransactions(query).stream().map(mapper::toResponse).toList());
  }

  @PostMapping
  public ApiResponse<LedgerTransactionResponse> recordLedgerTransaction(
      @PathVariable("ledgerId") String ledgerId,
      @Valid @RequestBody RecordLedgerTransactionRequest request,
      HttpServletRequest httpRequest) {
    var actor = LocalSessionContext.require(httpRequest);
    var command = mapper.toCommand(request, ledgerId, actor.actorId());
    return ApiResponse.success(mapper.toResponse(transactions.recordLedgerTransaction(command)));
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
