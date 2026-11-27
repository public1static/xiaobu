package com.xiaobu.ledger.infrastructure;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.repository.Repository;

interface FixedBillPaymentJpaRepository extends Repository<FixedBillPaymentEntity, String> {
  FixedBillPaymentEntity save(FixedBillPaymentEntity payment);

  boolean existsByTransactionId(String transactionId);

  Optional<FixedBillPaymentEntity> findByBillIdAndPeriod(String billId, LocalDate period);
}
