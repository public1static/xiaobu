package com.xiaobu.gift.application;

import java.util.List;

public interface GiftActivityQueryRepository {
  List<GiftActivityResult> findRecent(
      String ledgerId, String activityBookId, String defaultBookCode, String contactId);
}
