package com.xiaobu.gift.application;

import com.xiaobu.gift.domain.GiftDetail;
import java.util.Optional;

public interface GiftDetailRepository {
  Optional<GiftDetail> findActive(String ledgerId, String transactionId);

  void save(String ledgerId, String transactionId, String actorId, GiftDetail detail);

  void remove(String ledgerId, String transactionId, String actorId);
}
