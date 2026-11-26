package com.xiaobu.gift.application;

import com.xiaobu.gift.domain.GiftActivityBook;
import com.xiaobu.gift.domain.GiftContact;
import java.util.List;
import java.util.Optional;

public interface GiftDirectoryRepository {
  // 获取该账本已保存的有序事由。
  List<String> findReasons(String ledgerId);

  // 在当前事务内替换已核对的目录。
  void saveReasons(String ledgerId, List<String> names);

  List<GiftActivityBook> findBooks(String ledgerId);

  Optional<GiftActivityBook> findBook(String ledgerId, String id);

  void saveBook(String ledgerId, String actorId, GiftActivityBook book);

  void changeBookArchive(String ledgerId, String actorId, String id, boolean archived);

  List<GiftContact> findContacts(String ledgerId);

  Optional<GiftContact> findContact(String ledgerId, String id);

  void saveContact(String ledgerId, String actorId, GiftContact contact);
}
