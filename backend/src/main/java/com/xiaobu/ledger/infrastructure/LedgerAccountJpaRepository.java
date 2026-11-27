package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerAccountHistoryResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

interface LedgerAccountJpaRepository extends Repository<LedgerAccountEntity, String> {

  <S extends LedgerAccountEntity> S save(S entity);

  List<LedgerAccountEntity> findByLedgerIdOrderByNameAscIdAsc(String ledgerId);

  Optional<LedgerAccountEntity> findByLedgerIdAndId(String ledgerId, String id);

  boolean existsByLedgerIdAndId(String ledgerId, String id);

  // UNION ALL 合并不同资金来源，MySQL 对全部业务日与主键统一排序。
  @Query(
      value =
          """
          select t.id, t.direction as kind, cast(t.booked_on as char) as bookedOn,
                 t.amount_cents as amountCents, t.category_code as categoryCode, t.id as transactionId,
                 null as sourceAccountId, null as targetAccountId, t.note,
                 (t.created_by = :actorId) as ownRecord, null as relatedName
          from ledger_transactions t
          where t.ledger_id = :ledgerId and t.account_id = :accountId and t.deleted = 0
          union all
          select r.id, 'REFUND', cast(r.received_on as char), r.amount_cents, t.category_code, t.id,
                 null, null, '', (r.created_by = :actorId), null
          from ledger_refunds r
          join ledger_transactions t on t.ledger_id = r.ledger_id and t.id = r.transaction_id
          where r.ledger_id = :ledgerId and r.account_id = :accountId and t.deleted = 0
          union all
          select x.id, 'TRANSFER', cast(x.booked_on as char), x.amount_cents, '', null,
                 x.source_account_id, x.target_account_id, x.note, (x.created_by = :actorId), null
          from ledger_account_transfers x
          where x.ledger_id = :ledgerId and (x.source_account_id = :accountId or x.target_account_id = :accountId)
          union all
          select d.id, concat('DEPOSIT_', d.kind), cast(d.booked_on as char), d.amount_cents, '', null,
                 null, null, d.note, (d.created_by = :actorId), h.name
          from rental_deposits d
          join rental_contracts c on c.ledger_id = d.ledger_id and c.id = d.contract_id
          join rental_homes h on h.ledger_id = c.ledger_id and h.id = c.home_id
          where d.ledger_id = :ledgerId and d.account_id = :accountId and d.deleted = 0
          order by bookedOn desc, id desc
          """,
      nativeQuery = true)
  List<LedgerAccountHistoryResult> findHistory(String ledgerId, String accountId, String actorId);
}
