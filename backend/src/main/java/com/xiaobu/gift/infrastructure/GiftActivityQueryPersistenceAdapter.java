package com.xiaobu.gift.infrastructure;

import com.xiaobu.gift.application.GiftActivityFilter;
import com.xiaobu.gift.application.GiftActivityQueryRepository;
import com.xiaobu.gift.application.GiftActivityResult;
import com.xiaobu.gift.application.GiftActivityTotal;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class GiftActivityQueryPersistenceAdapter implements GiftActivityQueryRepository {
  private final GiftDetailJpaRepository details;

  GiftActivityQueryPersistenceAdapter(GiftDetailJpaRepository details) {
    this.details = details;
  }

  @Override
  public List<GiftActivityResult> findPage(GiftActivityFilter filter, int page, int pageSize) {
    return details.findPage(
        filter.ledgerId(),
        filter.activityBookId(),
        filter.dailyBook(),
        filter.contactId(),
        filter.keyword(),
        PageRequest.of(page, pageSize));
  }

  @Override
  public List<GiftActivityTotal> findTotals(GiftActivityFilter filter) {
    return details.findTotals(
        filter.ledgerId(),
        filter.activityBookId(),
        filter.dailyBook(),
        filter.contactId(),
        filter.keyword());
  }
}
