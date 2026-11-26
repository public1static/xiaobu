package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.CreateLedgerAccountCommand;
import com.xiaobu.ledger.application.LedgerAccountApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/accounts")
public class LedgerAccountController {

  private final LedgerAccountApplicationService accounts;
  private final LedgerAccountWebMapper mapper;

  LedgerAccountController(LedgerAccountApplicationService accounts, LedgerAccountWebMapper mapper) {
    this.accounts = accounts;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<List<LedgerAccountResponse>> listLedgerAccounts(
      @PathVariable("ledgerId") String ledgerId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        accounts.listLedgerAccounts(ledgerId, actor.actorId()).stream()
            .map(mapper::toResponse)
            .toList());
  }

  @PostMapping
  public ApiResponse<LedgerAccountResponse> createLedgerAccount(
      @PathVariable("ledgerId") String ledgerId,
      @Valid @RequestBody CreateLedgerAccountRequest input,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(
            accounts.createLedgerAccount(
                new CreateLedgerAccountCommand(
                    ledgerId,
                    actor.actorId(),
                    input.name(),
                    input.openingBalanceCents() == null ? 0 : input.openingBalanceCents(),
                    input.type(),
                    !Boolean.FALSE.equals(input.balanceKnown())))));
  }

  // 保存当前账户的名称、用途、余额及可用状态。
  @PutMapping("/{accountId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateLedgerAccount(
      @PathVariable String ledgerId,
      @PathVariable String accountId,
      @Valid @RequestBody UpdateLedgerAccountRequest input,
      HttpServletRequest request) {
    accounts.updateLedgerAccount(
        ledgerId,
        LedgerSessionContext.require(request).actorId(),
        accountId,
        input.name(),
        input.type(),
        input.active(),
        input.currentBalanceCents(),
        input.preserveBalance());
  }

  // 详情展示当前账本中该账户全部有效资金变动。
  @GetMapping("/{accountId}/history")
  public ApiResponse<List<LedgerAccountHistoryResponse>> listAccountHistory(
      @PathVariable String ledgerId, @PathVariable String accountId, HttpServletRequest request) {
    return ApiResponse.success(
        accounts
            .listAccountHistory(
                ledgerId, LedgerSessionContext.require(request).actorId(), accountId)
            .stream()
            .map(mapper::toResponse)
            .toList());
  }
}
