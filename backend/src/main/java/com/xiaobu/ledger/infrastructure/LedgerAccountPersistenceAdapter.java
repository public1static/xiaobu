package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.application.LedgerAccountRepository;
import com.xiaobu.ledger.domain.LedgerAccount;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class LedgerAccountPersistenceAdapter implements LedgerAccountRepository {

  private final LedgerAccountJpaRepository accounts;
  private final LedgerTransactionJpaRepository transactions;
  private final LedgerAccountPersistenceMapper mapper;
  private final LedgerRefundJpaRepository refunds;

  LedgerAccountPersistenceAdapter(
      LedgerAccountJpaRepository accounts,
      LedgerTransactionJpaRepository transactions,
      LedgerAccountPersistenceMapper mapper,
      LedgerRefundJpaRepository refunds) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.mapper = mapper;
    this.refunds = refunds;
  }

  @Override
  public void add(LedgerAccount account) {
    if (accounts.existsByLedgerIdAndName(account.ledgerId(), account.name())) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_ACCOUNT_NAME_CONFLICT"),
          ErrorCategory.CONFLICT,
          "当前账本已有同名账户",
          Map.of());
    }
    accounts.save(mapper.toNewEntity(account));
  }

  @Override
  public List<LedgerAccount> findByLedger(String ledgerId) {
    return accounts.findByLedgerIdOrderByNameAscIdAsc(ledgerId).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public Map<String, Long> findRecordedBalancesByLedger(String ledgerId) {
    Map<String, Long> balances = new HashMap<>();
    transactions
        .findAccountBalancesByLedger(ledgerId)
        .forEach(row -> balances.put(row.getAccountId(), row.getBalanceCents()));
    refunds
        .findAccountCredits(ledgerId)
        .forEach(row -> balances.merge(row.getAccountId(), row.getBalanceCents(), Long::sum));
    return Map.copyOf(balances);
  }

  @Override
  public Optional<LedgerAccount> findByLedgerAndId(String ledgerId, String accountId) {
    return accounts.findByLedgerIdAndId(ledgerId, accountId).map(mapper::toDomain);
  }

  @Override
  public boolean existsByLedgerAndId(String ledgerId, String accountId) {
    return accounts.existsByLedgerIdAndId(ledgerId, accountId);
  }
}
