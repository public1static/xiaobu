package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.LedgerInvitation;
import com.xiaobu.ledger.domain.LedgerMemberRole;
import com.xiaobu.ledger.domain.LedgerMembership;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class LedgerInvitationApplicationService {

  private static final Duration VALIDITY = Duration.ofHours(24);
  private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final IdPrefix MEMBERSHIP_PREFIX = new IdPrefix("member");
  private static final ErrorCode INVITATION_UNAVAILABLE =
      new ErrorCode("XIAOBU_INVITATION_UNAVAILABLE");
  private static final ErrorCode OWN_INVITATION = new ErrorCode("XIAOBU_OWN_INVITATION");

  private final PersonalLedgerApplicationService ledgers;
  private final LedgerInvitationRepository invitations;
  private final LedgerMembershipRepository memberships;
  private final EntityIdGenerator ids;

  LedgerInvitationApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerInvitationRepository invitations,
      LedgerMembershipRepository memberships,
      EntityIdGenerator ids) {
    this.ledgers = ledgers;
    this.invitations = invitations;
    this.memberships = memberships;
    this.ids = ids;
  }

  @Transactional
  public CreateLedgerInvitationResult createLedgerInvitation(
      CreateLedgerInvitationCommand command) {
    ledgers.requireOwnership(command.ledgerId(), command.actorId());
    byte[] secret = new byte[32];
    RANDOM.nextBytes(secret);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    Instant expiresAt = Instant.now().plus(VALIDITY);
    invitations.add(
        new LedgerInvitation(
            digest(token),
            command.ledgerId(),
            command.actorId(),
            command.displayName(),
            expiresAt,
            null));
    return new CreateLedgerInvitationResult(token, expiresAt);
  }

  @Transactional(readOnly = true)
  public LedgerInvitationPreviewResult previewLedgerInvitation(String token, String actorId) {
    var invitation = invitations.findByTokenDigest(digest(token)).orElseThrow(this::unavailable);
    boolean accepted = alreadyAcceptedBy(invitation, actorId);
    requireAvailable(invitation, accepted);
    if (actorId != null) {
      var membership = memberships.findByLedgerAndMember(invitation.ledgerId(), actorId);
      if ((accepted && membership.filter(LedgerMembership::isActive).isEmpty())
          || membership.filter(member -> invalidAfterRemoval(invitation, member)).isPresent()) {
        throw unavailable();
      }
    }
    return new LedgerInvitationPreviewResult(
        invitation.inviterName(), invitation.expiresAt(), accepted);
  }

  @Transactional
  public JoinedLedgerResult acceptLedgerInvitation(String token, String actorId) {
    String tokenDigest = digest(token);
    var invitation =
        invitations.findByTokenDigestForUpdate(tokenDigest).orElseThrow(this::unavailable);
    if (invitation.inviterId().equals(actorId)) {
      throw new FoundationException(
          OWN_INVITATION, ErrorCategory.CONFLICT, "不能接受自己发出的邀请", Map.of());
    }
    boolean accepted = alreadyAcceptedBy(invitation, actorId);
    requireAvailable(invitation, accepted);
    ledgers.lockLedger(invitation.ledgerId());
    var membership = memberships.findByLedgerAndMember(invitation.ledgerId(), actorId);
    if (accepted) {
      return membership
          .filter(LedgerMembership::isActive)
          .map(JoinedLedgerResult::from)
          .orElseThrow(this::unavailable);
    }
    if (invitation.isExpiredAt(Instant.now())
        || membership.filter(member -> invalidAfterRemoval(invitation, member)).isPresent()) {
      throw unavailable();
    }
    var joined =
        membership.orElseGet(() -> {
          var created =
              new LedgerMembership(
                  ids.next(MEMBERSHIP_PREFIX),
                  invitation.ledgerId(),
                  actorId,
                  invitation.inviterName(),
                  LedgerMemberRole.READ_ONLY,
                  null);
          memberships.add(created);
          return created;
        });
    if (!joined.isActive()) {
      memberships.restoreReadOnly(joined.id());
      joined =
          new LedgerMembership(
              joined.id(),
              joined.ledgerId(),
              joined.memberId(),
              joined.inviterName(),
              LedgerMemberRole.READ_ONLY,
              null);
    }
    invitations.markAccepted(tokenDigest, actorId);
    return JoinedLedgerResult.from(joined);
  }

  @Transactional(readOnly = true)
  public List<JoinedLedgerResult> listJoinedLedgers(String actorId) {
    return memberships.findActiveByMember(actorId).stream().map(JoinedLedgerResult::from).toList();
  }

  private static boolean invalidAfterRemoval(LedgerInvitation invitation, LedgerMembership member) {
    return !member.isActive()
        && !invitation.expiresAt().minus(VALIDITY).isAfter(member.removedAt());
  }

  private static boolean alreadyAcceptedBy(LedgerInvitation invitation, String actorId) {
    return actorId != null && actorId.equals(invitation.acceptedBy());
  }

  private void requireAvailable(LedgerInvitation invitation, boolean accepted) {
    if (!accepted && (invitation.acceptedBy() != null || invitation.isExpiredAt(Instant.now()))) {
      throw unavailable();
    }
  }

  private FoundationException unavailable() {
    return new FoundationException(
        INVITATION_UNAVAILABLE, ErrorCategory.NOT_FOUND, "邀请已失效或已被使用", Map.of());
  }

  private String digest(String token) {
    if (token == null || !TOKEN_FORMAT.matcher(token).matches()) {
      throw unavailable();
    }
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(token.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("无法使用 SHA-256 摘要算法", ex);
    }
  }
}
