package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.RentalContract;
import com.xiaobu.ledger.domain.RentalHome;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local-expense | wechat-preview")
public class RentalApplicationService {
  private static final IdPrefix HOME_PREFIX = new IdPrefix("rhome");
  private static final IdPrefix CONTRACT_PREFIX = new IdPrefix("lease");
  private static final IdPrefix BILL_PREFIX = new IdPrefix("bill");

  private final PersonalLedgerApplicationService ledgers;
  private final LedgerCategoryApplicationService categories;
  private final RentalRepository rentals;
  private final FixedBillRepository bills;
  private final EntityIdGenerator ids;

  RentalApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerCategoryApplicationService categories,
      RentalRepository rentals,
      FixedBillRepository bills,
      EntityIdGenerator ids) {
    this.ledgers = ledgers;
    this.categories = categories;
    this.rentals = rentals;
    this.bills = bills;
    this.ids = ids;
  }

  @Transactional(readOnly = true)
  public RentalPageResult<RentalHomeResult> listRentalHomes(
      String ledgerId, String actorId, int page) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return rentals.listHomes(ledgerId, page);
  }

  @Transactional
  public RentalHomeResult saveRentalHome(SaveRentalHomeCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var id =
        command.homeId() == null
            ? ids.next(HOME_PREFIX)
            : requireHome(command.ledgerId(), command.homeId()).id();
    var home =
        new RentalHome(id, command.ledgerId(), command.name(), command.address(), command.note());
    if (rentals.existsOtherHomeWithName(home.ledgerId(), home.id(), home.name())) {
      throw new FoundationException(new ErrorCode("XIAOBU_RENTAL_HOME_DUPLICATE"), "已有同名房屋");
    }
    if (command.homeId() == null) rentals.addHome(home, command.actorId());
    else rentals.updateHome(home, command.actorId());
    return RentalHomeResult.from(home, rentals.countHomeContracts(home.ledgerId(), home.id()));
  }

  @Transactional(readOnly = true)
  public RentalPageResult<RentalContractResult> listRentalContracts(
      String ledgerId, String actorId, String homeId, int page) {
    ledgers.requireReadAccess(ledgerId, actorId);
    if (homeId != null) requireHome(ledgerId, homeId);
    return rentals.listContracts(ledgerId, homeId, page);
  }

  @Transactional
  public RentalContractResult createRentalContract(CreateRentalContractCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var home = requireHome(command.ledgerId(), command.homeId());
    var contract =
        new RentalContract(
            ids.next(CONTRACT_PREFIX),
            home.ledgerId(),
            home.id(),
            command.landlordName(),
            command.startsOn(),
            command.endsOn(),
            command.monthlyRentCents(),
            command.depositCents(),
            command.cycle(),
            command.firstDueOn() == null ? null : ids.next(BILL_PREFIX),
            command.note());
    if (contract.billId() != null)
      addRentBill(contract, home, command.firstDueOn(), command.actorId());
    rentals.addContract(contract, command.actorId());
    return RentalContractResult.from(contract, home.name(), command.firstDueOn());
  }

  @Transactional
  public RentalContractResult generateRentalBill(GenerateRentalBillCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var existing =
        rentals
            .findContract(command.ledgerId(), command.contractId())
            .orElseThrow(() ->
                new FoundationException(new ErrorCode("XIAOBU_RENTAL_CONTRACT_INVALID"), "找不到该合同"));
    var home = requireHome(existing.ledgerId(), existing.homeId());
    if (existing.billId() != null) {
      var bill = bills.findBill(existing.ledgerId(), existing.billId()).orElseThrow();
      if (bill.firstDueOn().equals(command.firstDueOn())) {
        return RentalContractResult.from(existing, home.name(), bill.firstDueOn());
      }
      throw new FoundationException(
          new ErrorCode("XIAOBU_RENTAL_BILL_EXISTS"),
          ErrorCategory.CONFLICT,
          "该合同已设置房租账单",
          Map.of());
    }
    var contract = existing.withBill(ids.next(BILL_PREFIX));
    addRentBill(contract, home, command.firstDueOn(), command.actorId());
    rentals.linkBill(contract.ledgerId(), contract.id(), contract.billId(), command.actorId());
    return RentalContractResult.from(contract, home.name(), command.firstDueOn());
  }

  private void addRentBill(
      RentalContract contract, RentalHome home, LocalDate firstDueOn, String actorId) {
    var bill = contract.createRentBill(home.name(), firstDueOn);
    categories.requireActiveCategory(
        home.ledgerId(), TransactionDirection.EXPENSE, bill.categoryCode());
    bills.addBill(bill, actorId);
  }

  private RentalHome requireHome(String ledgerId, String homeId) {
    return rentals
        .findHome(ledgerId, homeId)
        .orElseThrow(
            () -> new FoundationException(new ErrorCode("XIAOBU_RENTAL_HOME_INVALID"), "找不到该房屋"));
  }
}
