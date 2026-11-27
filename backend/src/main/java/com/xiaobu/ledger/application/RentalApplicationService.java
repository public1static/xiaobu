package com.xiaobu.ledger.application;

import com.aegisid.foundation.data.id.EntityIdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.shared.error.ErrorCategory;
import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.gift.application.LedgerContactApplicationService;
import com.xiaobu.ledger.domain.FixedBill;
import com.xiaobu.ledger.domain.LedgerCategoryDefaults;
import com.xiaobu.ledger.domain.RentalContract;
import com.xiaobu.ledger.domain.RentalExpiryReminder;
import com.xiaobu.ledger.domain.RentalHome;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.time.YearMonth;
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
  private final LedgerContactApplicationService contacts;

  RentalApplicationService(
      PersonalLedgerApplicationService ledgers,
      LedgerCategoryApplicationService categories,
      RentalRepository rentals,
      FixedBillRepository bills,
      EntityIdGenerator ids,
      LedgerContactApplicationService contacts) {
    this.ledgers = ledgers;
    this.categories = categories;
    this.rentals = rentals;
    this.bills = bills;
    this.ids = ids;
    this.contacts = contacts;
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
    else {
      rentals.updateHome(home, command.actorId());
    }
    return RentalHomeResult.from(home, rentals.countHomeContracts(home.ledgerId(), home.id()));
  }

  @Transactional(readOnly = true)
  public RentalPageResult<RentalContractResult> listRentalContracts(
      String ledgerId, String actorId, String homeId, int page) {
    ledgers.requireReadAccess(ledgerId, actorId);
    if (homeId != null) requireHome(ledgerId, homeId);
    return rentals.listContracts(ledgerId, homeId, page);
  }

  @Transactional(readOnly = true)
  public RentalContractResult getRentalContract(
      String ledgerId, String actorId, String contractId) {
    ledgers.requireReadAccess(ledgerId, actorId);
    return rentals
        .findContractDetails(ledgerId, contractId)
        .orElseThrow(() ->
            new FoundationException(
                new ErrorCode("XIAOBU_RENTAL_CONTRACT_INVALID"),
                ErrorCategory.NOT_FOUND,
                "找不到该合同",
                Map.of()));
  }

  @Transactional
  public RentalContractResult createRentalContract(CreateRentalContractCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var home = requireHome(command.ledgerId(), command.homeId());
    return createContract(command, home, null);
  }

  @Transactional
  public RentalContractResult renewRentalContract(RenewRentalContractCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var previous =
        rentals
            .findContract(command.ledgerId(), command.contractId())
            .orElseThrow(() -> renewalConflict("XIAOBU_RENTAL_RENEWAL_CHANGED", "原合同已不可用"));
    if (!previous.endsOn().equals(command.expectedEndsOn())) {
      throw renewalConflict("XIAOBU_RENTAL_RENEWAL_CHANGED", "原合同到期日已变更，请重新打开");
    }
    if (rentals.hasRenewal(previous.ledgerId(), previous.id())) {
      throw renewalConflict("XIAOBU_RENTAL_ALREADY_RENEWED", "这份合同已办理续租");
    }
    if (command.startsOn() == null || !command.startsOn().isAfter(previous.endsOn())) {
      throw renewalConflict("XIAOBU_RENTAL_RENEWAL_START", "续租须在原合同到期后开始");
    }
    var home = requireHome(previous.ledgerId(), previous.homeId());
    return createContract(
        new CreateRentalContractCommand(
            previous.ledgerId(),
            command.actorId(),
            home.id(),
            command.landlordName(),
            command.startsOn(),
            command.endsOn(),
            command.monthlyRentCents(),
            command.depositCents(),
            command.cycle(),
            command.firstDueOn(),
            command.note(),
            command.existingBillId(),
            command.remindDays(),
            command.landlordContactId(),
            command.landlordDistinction()),
        home,
        previous.id());
  }

  private RentalContractResult createContract(
      CreateRentalContractCommand command, RentalHome home, String previousContractId) {
    if (command.existingBillId() != null && command.firstDueOn() != null) {
      throw new FoundationException(new ErrorCode("XIAOBU_RENTAL_BILL_INVALID"), "请选择生成或关联账单");
    }
    var billId =
        command.existingBillId() != null
            ? command.existingBillId()
            : command.firstDueOn() == null ? null : ids.next(BILL_PREFIX);
    var landlord =
        contacts.resolveRentalContact(
            home.ledgerId(),
            command.actorId(),
            command.landlordContactId(),
            command.landlordName(),
            command.landlordDistinction());
    var contract =
        new RentalContract(
            ids.next(CONTRACT_PREFIX),
            home.ledgerId(),
            home.id(),
            landlord.name(),
            command.startsOn(),
            command.endsOn(),
            command.monthlyRentCents(),
            command.depositCents(),
            command.cycle(),
            billId,
            command.note(),
            command.remindDays() == null ? RentalExpiryReminder.DEFAULT_DAYS : command.remindDays(),
            null,
            previousContractId,
            landlord.id(),
            landlord.distinction());
    if (rentals.hasOverlappingContract(
        contract.ledgerId(),
        contract.homeId(),
        contract.startsOn(),
        contract.endsOn(),
        previousContractId == null)) {
      throw renewalConflict("XIAOBU_RENTAL_RENEWAL_OVERLAP", "与该房屋已有合同的租期重叠");
    }
    FixedBill bill = null;
    if (command.existingBillId() != null) {
      bill = adoptRentBill(contract, command.actorId());
    } else if (billId != null) {
      bill = addRentBill(contract, home, command.firstDueOn(), command.actorId());
    }
    rentals.addContract(contract, command.actorId());
    return RentalContractResult.from(contract, home.name(), bill, null);
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
        return rentals.findContractDetails(existing.ledgerId(), existing.id()).orElseThrow();
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
    return rentals.findContractDetails(contract.ledgerId(), contract.id()).orElseThrow();
  }

  @Transactional(readOnly = true)
  public FixedBillPageResult listPendingRentalPeriods(
      String ledgerId, String actorId, YearMonth month, int page) {
    ledgers.requireReadAccess(ledgerId, actorId);
    if (month == null || month.getYear() < 1900 || month.getYear() > 2100) {
      throw new FoundationException(new ErrorCode("XIAOBU_RENTAL_PERIOD_INVALID"), "请选择有效账期");
    }
    return bills.listPendingRentalPeriods(ledgerId, month, page);
  }

  @Transactional(readOnly = true)
  public RentalPageResult<RentalBillPlanResult> listRentalBillPlans(
      ListRentalBillPlansQuery query) {
    ledgers.requireReadAccess(query.ledgerId(), query.actorId());
    if (query.cycle() == null
        || query.startsOn() == null
        || query.endsOn() == null
        || query.startsOn().getYear() < 1900
        || query.endsOn().getYear() > 2100
        || query.endsOn().isBefore(query.startsOn())) {
      throw new FoundationException(new ErrorCode("XIAOBU_RENTAL_BILL_INVALID"), "请先填写有效租期");
    }
    return bills.listRentalPlanCandidates(
        query.ledgerId(), query.cycle().months(), query.startsOn(), query.endsOn(), query.page());
  }

  @Transactional
  public RentalContractResult linkRentalBill(LinkRentalBillCommand command) {
    ledgers.requireWriteAccess(command.ledgerId(), command.actorId());
    var existing =
        rentals
            .findContract(command.ledgerId(), command.contractId())
            .orElseThrow(() -> planConflict("找不到该合同"));
    if (existing.billId() != null) {
      if (!existing.billId().equals(command.billId())) {
        throw planConflict("该合同已设置房租账单");
      }
      return rentals.findContractDetails(existing.ledgerId(), existing.id()).orElseThrow();
    }
    var contract = existing.withBill(command.billId());
    adoptRentBill(contract, command.actorId());
    rentals.linkBill(contract.ledgerId(), contract.id(), contract.billId(), command.actorId());
    return rentals.findContractDetails(contract.ledgerId(), contract.id()).orElseThrow();
  }

  private FixedBill adoptRentBill(RentalContract contract, String actorId) {
    requireNoOverlappingPlan(contract);
    var bill =
        bills
            .findBill(contract.ledgerId(), contract.billId())
            .filter(row ->
                row.stopMonth() == null
                    && row.lastDueOn() == null
                    && LedgerCategoryDefaults.HOUSING.name().equals(row.categoryCode())
                    && row.cycleMonths() == contract.cycle().months())
            .orElseThrow(() -> planConflict("请选择同周期且仍在使用的住房账单"));
    if (rentals.hasContractForBill(bill.id())) throw planConflict("该账单已关联其它合同");
    var firstDueOn = bill.firstDueOnOrAfter(contract.startsOn());
    if (firstDueOn.isAfter(contract.endsOn())) throw planConflict("账单与租期没有重叠");
    categories.requireActiveCategory(
        contract.ledgerId(), TransactionDirection.EXPENSE, bill.categoryCode());
    var adopted =
        bill.adoptForRental(contract.startsOn(), contract.endsOn(), contract.periodAmountCents());
    bills.adoptForRental(adopted, actorId);
    return adopted;
  }

  private void requireNoOverlappingPlan(RentalContract contract) {
    if (rentals.hasOverlappingRentalBill(
        contract.ledgerId(),
        contract.homeId(),
        contract.id(),
        contract.startsOn(),
        contract.endsOn())) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_RENTAL_HOME_ALREADY_BILLED"),
          ErrorCategory.CONFLICT,
          "这套房屋在该租期内已有房租账单",
          Map.of());
    }
  }

  private static FoundationException renewalConflict(String code, String message) {
    return new FoundationException(new ErrorCode(code), ErrorCategory.CONFLICT, message, Map.of());
  }

  private static FoundationException planConflict(String message) {
    return new FoundationException(
        new ErrorCode("XIAOBU_RENTAL_PLAN_CONFLICT"), ErrorCategory.CONFLICT, message, Map.of());
  }

  private FixedBill addRentBill(
      RentalContract contract, RentalHome home, LocalDate firstDueOn, String actorId) {
    requireNoOverlappingPlan(contract);
    var bill = contract.createRentBill(home.name(), firstDueOn);
    categories.requireActiveCategory(
        home.ledgerId(), TransactionDirection.EXPENSE, bill.categoryCode());
    bills.addBill(bill, actorId);
    return bill;
  }

  private RentalHome requireHome(String ledgerId, String homeId) {
    return rentals
        .findHome(ledgerId, homeId)
        .orElseThrow(
            () -> new FoundationException(new ErrorCode("XIAOBU_RENTAL_HOME_INVALID"), "找不到该房屋"));
  }
}
