package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.interfaces.LedgerSessionContext;
import com.xiaobu.ledger.application.CreateLedgerInvitationCommand;
import com.xiaobu.ledger.application.LedgerInvitationApplicationService;
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
@RequestMapping("/api/v1")
public class LedgerInvitationController {

  private final LedgerInvitationApplicationService invitations;
  private final LedgerInvitationWebMapper mapper;

  LedgerInvitationController(
      LedgerInvitationApplicationService invitations, LedgerInvitationWebMapper mapper) {
    this.invitations = invitations;
    this.mapper = mapper;
  }

  @PostMapping("/ledgers/{ledgerId}/invitations")
  public ApiResponse<CreateLedgerInvitationResponse> createLedgerInvitation(
      @PathVariable("ledgerId") String ledgerId, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    var command = new CreateLedgerInvitationCommand(ledgerId, actor.actorId(), actor.displayName());
    return ApiResponse.success(mapper.toResponse(invitations.createLedgerInvitation(command)));
  }

  @PostMapping("/ledger-invitations/preview")
  public ApiResponse<LedgerInvitationPreviewResponse> previewLedgerInvitation(
      @Valid @RequestBody RespondToLedgerInvitationRequest input, HttpServletRequest request) {
    var actor = LedgerSessionContext.current(request);
    var actorId = actor == null ? null : actor.actorId();
    return ApiResponse.success(
        mapper.toResponse(invitations.previewLedgerInvitation(input.token(), actorId)));
  }

  @PostMapping("/ledger-invitations/accept")
  public ApiResponse<JoinedLedgerResponse> acceptLedgerInvitation(
      @Valid @RequestBody RespondToLedgerInvitationRequest input, HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        mapper.toResponse(invitations.acceptLedgerInvitation(input.token(), actor.actorId())));
  }

  @GetMapping("/ledgers/joined")
  public ApiResponse<List<JoinedLedgerResponse>> listJoinedLedgers(HttpServletRequest request) {
    var actor = LedgerSessionContext.require(request);
    return ApiResponse.success(
        invitations.listJoinedLedgers(actor.actorId()).stream().map(mapper::toResponse).toList());
  }
}
