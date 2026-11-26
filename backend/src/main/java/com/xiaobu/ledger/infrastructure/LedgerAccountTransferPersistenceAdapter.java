package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerAccountTransferRepository;
import com.xiaobu.ledger.domain.LedgerAccountTransfer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class LedgerAccountTransferPersistenceAdapter implements LedgerAccountTransferRepository {

  private final LedgerAccountTransferJpaRepository transfers;
  private final LedgerAccountTransferPersistenceMapper mapper;

  LedgerAccountTransferPersistenceAdapter(
      LedgerAccountTransferJpaRepository transfers, LedgerAccountTransferPersistenceMapper mapper) {
    this.transfers = transfers;
    this.mapper = mapper;
  }

  @Override
  public void add(LedgerAccountTransfer transfer) {
    transfers.save(mapper.toNewEntity(transfer));
  }

  @Override
  public Map<String, Long> findNetByLedger(String ledgerId) {
    Map<String, Long> net = new HashMap<>();
    transfers
        .findIncomingTotals(ledgerId)
        .forEach(row -> net.merge(row.getAccountId(), row.getBalanceCents(), Long::sum));
    transfers
        .findOutgoingTotals(ledgerId)
        .forEach(row -> net.merge(row.getAccountId(), -row.getBalanceCents(), Long::sum));
    return Map.copyOf(net);
  }

  @Override
  public List<LedgerAccountTransfer> findRecentByLedger(String ledgerId) {
    return transfers.findTop50ByLedgerIdOrderByBookedOnDescIdDesc(ledgerId).stream()
        .map(mapper::toDomain)
        .toList();
  }

  // 加载原始转账资料以验证修改权限。
  public Optional<LedgerAccountTransfer> findByLedgerAndId(String ledgerId, String id) {
    return transfers.findByLedgerIdAndId(ledgerId, id).map(mapper::toDomain);
  }

  // 在持有账本锁的事务中更新现有记录。
  public void update(LedgerAccountTransfer transfer) {
    transfers
        .findByLedgerIdAndId(transfer.ledgerId(), transfer.id())
        .orElseThrow()
        .revise(transfer);
  }

  // 移除转账后余额汇总自动撤回两端影响。
  public void cancel(String ledgerId, String id) {
    transfers.deleteByLedgerIdAndId(ledgerId, id);
  }
}
