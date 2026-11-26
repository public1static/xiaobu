package com.xiaobu.gift.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface GiftReasonJpaRepository extends JpaRepository<GiftReasonEntity, Long> {
  // 按账本内的显示顺序读取名称。
  @Query(
      "select r.name from GiftReasonEntity r where r.ledgerId = :ledgerId order by r.displayOrder")
  List<String> findNames(String ledgerId);

  // 替换时先移除旧位置，调用方已锁定本账本。
  @Modifying
  @Query("delete from GiftReasonEntity r where r.ledgerId = :ledgerId")
  void clearDirectory(String ledgerId);
}
