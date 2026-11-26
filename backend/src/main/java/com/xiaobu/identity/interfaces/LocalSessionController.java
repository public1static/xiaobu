package com.xiaobu.identity.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.ledger.application.PersonalLedgerApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Loopback test sign-in only; this endpoint does not verify a real identity. */
@RestController
@Profile("local-expense")
@RequestMapping("/api/v1/local-session")
public class LocalSessionController {

  private final PersonalLedgerApplicationService ledgers;

  LocalSessionController(PersonalLedgerApplicationService ledgers) {
    this.ledgers = ledgers;
  }

  @PostMapping
  public ApiResponse<LocalSessionResponse> openLocalSession(
      @Valid @RequestBody OpenLocalSessionRequest input, HttpServletRequest request) {
    var account = LocalPreviewAccount.fromSelector(input.account());
    var ledger = ledgers.openPersonalLedger(account.actorId());
    var context = new LocalSessionContext(account.actorId(), account.displayName(), ledger.id());
    LocalSessionContext.open(request, context);
    return ApiResponse.success(toResponse(context));
  }

  @GetMapping
  public ApiResponse<LocalSessionResponse> getLocalSession(HttpServletRequest request) {
    return ApiResponse.success(toResponse(LocalSessionContext.require(request)));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void closeLocalSession(HttpServletRequest request) {
    LocalSessionContext.close(request);
  }

  private static LocalSessionResponse toResponse(LocalSessionContext context) {
    return new LocalSessionResponse(context.displayName(), context.ledgerId());
  }
}
