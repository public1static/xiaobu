package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.LedgerCategory;
import java.util.List;

public interface LedgerCategoryRepository {

  List<LedgerCategory> findByLedger(String ledgerId);

  void save(LedgerCategory category);
}
