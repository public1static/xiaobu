package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerInvitation;
import java.util.Optional;

public interface LedgerInvitationRepository {

  void add(LedgerInvitation invitation);

  Optional<LedgerInvitation> findByTokenDigest(String tokenDigest);

  Optional<LedgerInvitation> findByTokenDigestForUpdate(String tokenDigest);

  void markAccepted(String tokenDigest, String actorId);
}
