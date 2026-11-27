package com.xiaobu.ledger.application;

import com.xiaobu.ledger.domain.RentalContract;
import com.xiaobu.ledger.domain.RentalHome;
import java.util.Optional;

public interface RentalRepository {
  Optional<RentalHome> findHome(String ledgerId, String homeId);

  boolean existsOtherHomeWithName(String ledgerId, String homeId, String name);

  void addHome(RentalHome home, String actorId);

  void updateHome(RentalHome home, String actorId);

  long countHomeContracts(String ledgerId, String homeId);

  RentalPageResult<RentalHomeResult> listHomes(String ledgerId, int page);

  RentalPageResult<RentalContractResult> listContracts(String ledgerId, String homeId, int page);

  Optional<RentalContract> findContract(String ledgerId, String contractId);

  void linkBill(String ledgerId, String contractId, String billId, String actorId);

  void addContract(RentalContract contract, String actorId);
}
