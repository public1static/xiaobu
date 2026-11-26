package com.xiaobu.ledger.infrastructure;

import com.xiaobu.ledger.application.LedgerAccountTransferRepository;
import com.xiaobu.ledger.domain.LedgerAccountTransfer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
}
