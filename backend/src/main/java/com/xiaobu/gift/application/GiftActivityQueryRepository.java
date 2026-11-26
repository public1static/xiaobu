package com.xiaobu.gift.application;

import java.util.List;

public interface GiftActivityQueryRepository {
  List<GiftActivityResult> findPage(GiftActivityFilter filter, int page, int pageSize);

  List<GiftActivityTotal> findTotals(GiftActivityFilter filter);
}
