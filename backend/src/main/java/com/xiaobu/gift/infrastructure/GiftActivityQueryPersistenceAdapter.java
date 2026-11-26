package com.xiaobu.gift.infrastructure;

import com.xiaobu.gift.application.GiftActivityQueryRepository;
import com.xiaobu.gift.application.GiftActivityResult;
import com.xiaobu.gift.domain.GiftBook;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
class GiftActivityQueryPersistenceAdapter implements GiftActivityQueryRepository {
  private final GiftDetailJpaRepository details;

  GiftActivityQueryPersistenceAdapter(GiftDetailJpaRepository details) {
    this.details = details;
  }

  @Override
  public List<GiftActivityResult> findRecent(
      String ledgerId, String activityBookId, String defaultBookCode, String contactId) {
    return details.findRecent(
        ledgerId,
        activityBookId,
        defaultBookCode == null ? null : GiftBook.valueOf(defaultBookCode),
        contactId,
        Pageable.ofSize(100));
  }
}
