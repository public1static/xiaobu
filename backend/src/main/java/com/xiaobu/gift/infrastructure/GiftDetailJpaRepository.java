package com.xiaobu.gift.infrastructure;

import com.xiaobu.gift.application.GiftActivityResult;
import com.xiaobu.gift.application.GiftActivityTotal;
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

  String FILTER =
      """
      from GiftDetailEntity d
      join LedgerTransactionEntity t on t.id = d.transactionId and t.ledgerId = d.ledgerId
      where d.ledgerId = :ledgerId and d.deleted = false and t.deleted = false
        and (:activity is null or d.bookId = :activity)
        and (:daily is null or (d.bookId is null and d.book = :daily))
        and (:contact is null or d.contactId = :contact)
        and (:keyword = ''
             or locate(lower(:keyword), lower(coalesce(d.personName, ''))) > 0
             or locate(lower(:keyword), lower(coalesce(d.distinction, ''))) > 0
             or locate(lower(:keyword), lower(coalesce(d.reason, ''))) > 0
             or locate(lower(:keyword), lower(t.note)) > 0)
      """;

  @Query(
      """
      select new com.xiaobu.gift.application.GiftActivityResult(
          t.id, d.bookId, d.contactId, d.personName, d.distinction, d.reason, t.note,
          t.bookedOn, t.amountCents, t.direction)
      """
          + FILTER
          + " order by t.bookedOn desc, t.id desc")
  List<GiftActivityResult> findPage(
      @Param("ledgerId") String ledgerId,
      @Param("activity") String activity,
      @Param("daily") GiftBook daily,
      @Param("contact") String contact,
      @Param("keyword") String keyword,
      Pageable page);

  @Query(
      """
      select new com.xiaobu.gift.application.GiftActivityTotal(t.direction, sum(t.amountCents), count(t.id))
      """
          + FILTER
          + " group by t.direction")
  List<GiftActivityTotal> findTotals(
      @Param("ledgerId") String ledgerId,
      @Param("activity") String activity,
      @Param("daily") GiftBook daily,
      @Param("contact") String contact,
      @Param("keyword") String keyword);
}
