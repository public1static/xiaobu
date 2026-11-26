package com.xiaobu.gift.infrastructure;

import com.xiaobu.gift.application.GiftActivityResult;
import com.xiaobu.gift.domain.GiftBook;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GiftDetailJpaRepository extends JpaRepository<GiftDetailEntity, String> {
  Optional<GiftDetailEntity> findByLedgerIdAndTransactionId(String ledgerId, String transactionId);

  Optional<GiftDetailEntity> findByLedgerIdAndTransactionIdAndDeletedFalse(
      String ledgerId, String transactionId);

  @Query(
      """
      select new com.xiaobu.gift.application.GiftActivityResult(
          t.id, d.bookId, d.contactId, d.personName, d.distinction, d.reason, t.note,
          t.bookedOn, t.amountCents, t.direction)
      from GiftDetailEntity d
      join LedgerTransactionEntity t on t.id = d.transactionId and t.ledgerId = d.ledgerId
      where d.ledgerId = :ledgerId and d.deleted = false and t.deleted = false
        and (:activity is null or d.bookId = :activity)
        and (:daily is null or (d.bookId is null and d.book = :daily))
        and (:contact is null or d.contactId = :contact)
      order by t.bookedOn desc, t.id desc
      """)
  List<GiftActivityResult> findRecent(
      @Param("ledgerId") String ledgerId,
      @Param("activity") String activity,
      @Param("daily") GiftBook daily,
      @Param("contact") String contact,
      Pageable page);
}
