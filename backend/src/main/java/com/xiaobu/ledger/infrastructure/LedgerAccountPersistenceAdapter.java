package com.xiaobu.ledger.infrastructure;

import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.application.LedgerAccountHistoryResult;
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
  private final RentalDepositJpaRepository rentalDeposits;

  LedgerAccountPersistenceAdapter(
      LedgerAccountJpaRepository accounts,
      LedgerTransactionJpaRepository transactions,
      LedgerAccountPersistenceMapper mapper,
      LedgerRefundJpaRepository refunds,
      RentalDepositJpaRepository rentalDeposits) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.mapper = mapper;
    this.refunds = refunds;
    this.rentalDeposits = rentalDeposits;
  }

  @Override
  public void add(LedgerAccount account) {
    requireUniqueName(account);
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
    rentalDeposits
        .findAccountMovements(ledgerId)
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

  // 账本写锁由调用方持有，重名检查与修改处于同一事务。
  public void update(LedgerAccount account) {
    var entity = accounts.findByLedgerIdAndId(account.ledgerId(), account.id()).orElseThrow();
    if (!account.name().equals(entity.getName())) requireUniqueName(account);
    entity.revise(account);
  }

  // 按原型统一比较大小写和全半角，改名时排除原账户。
  private void requireUniqueName(LedgerAccount account) {
    String key = normalizedName(account.name());
    if (accounts.findByLedgerIdOrderByNameAscIdAsc(account.ledgerId()).stream()
        .anyMatch(other ->
            !other.getId().equals(account.id()) && normalizedName(other.getName()).equals(key))) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_ACCOUNT_NAME_CONFLICT"),
          ErrorCategory.CONFLICT,
          "当前账本已有同名账户",
          Map.of());
    }
  }

  // 对账户名称应用 Unicode 兼容性规范化。
  private String normalizedName(String name) {
    return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKC)
        .toLowerCase(java.util.Locale.ROOT);
  }

  // 统一排序收支、退款与转账，不受最近五十笔上限影响。
  public List<LedgerAccountHistoryResult> findHistory(
      String ledgerId, String accountId, String actorId) {
    return accounts.findHistory(ledgerId, accountId, actorId);
  }
}
