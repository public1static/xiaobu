package com.xiaobu.gift.application;

import com.xiaobu.gift.domain.GiftActivityBook;
import com.xiaobu.gift.domain.GiftContact;
import java.util.List;
import java.util.Optional;

public interface GiftDirectoryRepository {
  List<GiftActivityBook> findBooks(String ledgerId);

  Optional<GiftActivityBook> findBook(String ledgerId, String id);

  void saveBook(String ledgerId, String actorId, GiftActivityBook book);

  void changeBookArchive(String ledgerId, String actorId, String id, boolean archived);

  List<GiftContact> findContacts(String ledgerId);

  Optional<GiftContact> findContact(String ledgerId, String id);

  void saveContact(String ledgerId, String actorId, GiftContact contact);
}
