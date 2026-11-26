package com.xiaobu.gift.infrastructure;

import com.xiaobu.gift.application.GiftDirectoryRepository;
import com.xiaobu.gift.domain.GiftActivityBook;
import com.xiaobu.gift.domain.GiftContact;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class GiftDirectoryPersistenceAdapter implements GiftDirectoryRepository {
  private final GiftActivityBookJpaRepository books;
  private final GiftContactJpaRepository contacts;
  // 按账本隔离存储有序礼金事由。
  private final GiftReasonJpaRepository reasons;

  GiftDirectoryPersistenceAdapter(
      GiftActivityBookJpaRepository books,
      GiftContactJpaRepository contacts,
      GiftReasonJpaRepository reasons) {
    this.reasons = reasons;
    this.books = books;
    this.contacts = contacts;
  }

  // 由数据库按显示位置排序。
  @Override
  public List<String> findReasons(String ledgerId) {
    return reasons.findNames(ledgerId);
  }

  // 同一事务内保存目录的完整顺序。
  @Override
  public void saveReasons(String ledgerId, List<String> names) {
    reasons.clearDirectory(ledgerId);
    for (int i = 0; i < names.size(); i++)
      reasons.save(new GiftReasonEntity(ledgerId, names.get(i), i));
  }

  @Override
  public List<GiftActivityBook> findBooks(String ledgerId) {
    return books.findByLedgerIdOrderByCreatedAtDesc(ledgerId).stream()
        .map(GiftActivityBookEntity::toDomain)
        .toList();
  }

  @Override
  public Optional<GiftActivityBook> findBook(String ledgerId, String id) {
    return books.findByLedgerIdAndId(ledgerId, id).map(GiftActivityBookEntity::toDomain);
  }

  @Override
  public void saveBook(String ledgerId, String actorId, GiftActivityBook book) {
    books.save(new GiftActivityBookEntity(ledgerId, actorId, book));
  }

  @Override
  public void changeBookArchive(String ledgerId, String actorId, String id, boolean archived) {
    books.findByLedgerIdAndId(ledgerId, id).orElseThrow().changeArchive(actorId, archived);
  }

  @Override
  public List<GiftContact> findContacts(String ledgerId) {
    return contacts.findByLedgerIdOrderByNameAscDistinctionAsc(ledgerId).stream()
        .map(GiftContactEntity::toDomain)
        .toList();
  }

  @Override
  public Optional<GiftContact> findContact(String ledgerId, String id) {
    return contacts.findByLedgerIdAndId(ledgerId, id).map(GiftContactEntity::toDomain);
  }

  @Override
  public void saveContact(String ledgerId, String actorId, GiftContact contact) {
    contacts.save(new GiftContactEntity(ledgerId, actorId, contact));
  }
}
