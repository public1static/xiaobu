package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.FixedBillPaymentReferenceResult;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface FixedBillPaymentJpaRepository extends Repository<FixedBillPaymentEntity, String> {
  FixedBillPaymentEntity save(FixedBillPaymentEntity payment);

  boolean existsByTransactionIdAndDeletedFalse(String transactionId);

  Optional<FixedBillPaymentEntity> findByBillIdAndPeriodAndDeletedFalse(
      String billId, LocalDate period);

  Optional<FixedBillPaymentEntity> findByIdAndDeletedFalse(String id);

  @Query(
      """
      select new com.xiaobu.ledger.application.FixedBillPaymentReferenceResult(p.id, b.name, p.period)
      from FixedBillPaymentEntity p join FixedBillEntity b on b.id = p.billId
      where b.ledgerId = :ledgerId and b.deleted = false
          and p.transactionId = :transactionId and p.deleted = false
      """)
  Optional<FixedBillPaymentReferenceResult> findPaymentReference(
      String ledgerId, String transactionId);
}
