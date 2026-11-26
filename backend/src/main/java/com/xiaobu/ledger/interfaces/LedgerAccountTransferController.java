package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.LedgerAccountTransferApplicationService;
import com.xiaobu.ledger.application.RecordLedgerAccountTransferCommand;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-expense | wechat-preview")
@RequestMapping("/api/v1/ledgers/{ledgerId}/account-transfers")
public class LedgerAccountTransferController {

  private final LedgerAccountTransferApplicationService transfers;
  private final LedgerAccountTransferWebMapper mapper;

  LedgerAccountTransferController(
      LedgerAccountTransferApplicationService transfers, LedgerAccountTransferWebMapper mapper) {
    this.transfers = transfers;
    this.mapper = mapper;
  }

  @GetMapping
  public ApiResponse<List<LedgerAccountTransferResponse>> listRecentTransfers(
      @PathVariable("ledgerId") String ledgerId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        transfers.listRecentTransfers(ledgerId, actor.actorId()).stream()
            .map(mapper::toResponse)
            .toList());
  }

  @PostMapping
  public ApiResponse<LedgerAccountTransferResponse> recordTransfer(
      @PathVariable("ledgerId") String ledgerId,
      @Valid @RequestBody RecordLedgerAccountTransferRequest input,
      HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    var result =
        transfers.recordTransfer(
            new RecordLedgerAccountTransferCommand(
                ledgerId,
                actor.actorId(),
                input.sourceAccountId(),
                input.targetAccountId(),
                input.amountCents(),
                input.bookedOn(),
                input.note()));
    return ApiResponse.success(mapper.toResponse(result));
  }
}
