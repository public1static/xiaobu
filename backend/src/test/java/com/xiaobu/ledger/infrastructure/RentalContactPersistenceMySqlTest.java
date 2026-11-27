package com.xiaobu.ledger.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.boot.XiaobuApplication;
import com.xiaobu.gift.application.CreateGiftContactCommand;
import com.xiaobu.gift.application.GiftDetailDraft;
import com.xiaobu.gift.application.GiftDirectoryApplicationService;
import com.xiaobu.gift.application.LedgerContactApplicationService;
import com.xiaobu.ledger.application.CreateRentalContractCommand;
import com.xiaobu.ledger.application.LedgerTransactionApplicationService;
import com.xiaobu.ledger.application.RecordLedgerTransactionCommand;
import com.xiaobu.ledger.application.RenewRentalContractCommand;
import com.xiaobu.ledger.application.RentalApplicationService;
import com.xiaobu.ledger.application.RentalContractResult;
import com.xiaobu.ledger.application.SaveRentalHomeCommand;
import com.xiaobu.ledger.application.TransactionExtensionChange;
import com.xiaobu.ledger.domain.RentalPaymentCycle;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@EnabledIfEnvironmentVariable(
    named = "XIAOBU_DEV_DB_URL",
    matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/xiaobu_expense_test(?:\\?.*)?")
@SpringBootTest(
    classes = XiaobuApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("local-expense")
class RentalContactPersistenceMySqlTest {
  private static final LocalDate START = LocalDate.of(2025, 1, 1);
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RentalApplicationService rentals;
  @Autowired private LedgerContactApplicationService contacts;
  @Autowired private GiftDirectoryApplicationService gifts;
  @Autowired private LedgerTransactionApplicationService transactions;

  @Test
  void sameNameRentalsStayOutOfGiftDirectoryUntilUsedInAGift() {
    var f = fixture();
    var outsider = fixture();
    var friend =
        gifts.createGiftContact(new CreateGiftContactCommand(f.ledger(), f.actor(), "周先生", "同事"));
    var asLandlord = create(f, "周先生", null, null);
    assertEquals(friend.id(), asLandlord.landlordContactId());
    var newLandlord = create(f, "周先生", null, "房东");
    assertNotEquals(friend.id(), newLandlord.landlordContactId());
    assertEquals("房东", newLandlord.landlordDistinction());
    assertEquals(2, contacts.listContactMatches(f.ledger(), f.actor(), "周先生", 0).totalCount());
    assertEquals(
        List.of(friend.id()),
        gifts.listGiftContacts(f.ledger(), f.actor()).stream().map(row -> row.id()).toList());
    assertEquals(
        0, contacts.listContactMatches(outsider.ledger(), outsider.actor(), "周先生", 0).totalCount());
    assertThrows(FoundationException.class, () -> create(f, "周先生", null, null));
    assertThrows(FoundationException.class, () -> create(f, "周先生", null, "房东"));
    assertThrows(
        FoundationException.class,
        () -> create(outsider, "周先生", newLandlord.landlordContactId(), null));
    var same = create(f, "临时输入", newLandlord.landlordContactId(), null);
    assertEquals(newLandlord.landlordContactId(), same.landlordContactId());
    assertEquals("周先生", same.landlordName());
    assertEquals(3, rentals.listRentalContracts(f.ledger(), f.actor(), f.home(), 0).totalCount());

    transactions.recordLedgerTransaction(
        new RecordLedgerTransactionCommand(
            f.ledger(),
            f.actor(),
            TransactionDirection.EXPENSE,
            20000,
            START,
            "GIFT_SENT",
            "",
            null,
            List.of(
                new TransactionExtensionChange(
                    "GIFT",
                    TransactionExtensionChange.Operation.UPSERT,
                    new GiftDetailDraft(
                        "周先生", "乔迁", null, newLandlord.landlordContactId(), false, "房东")))));
    assertEquals(2, gifts.listGiftContacts(f.ledger(), f.actor()).size());
    assertEquals(2, contacts.listContactMatches(f.ledger(), f.actor(), "周先生", 0).totalCount());
  }

  @Test
  void appliesServerPagingAndKeepsLegacySignedNamesAndRenewalSnapshots() {
    var f = fixture();
    for (int i = 0; i < 21; i++) {
      create(f, "李女士", null, "出租方 " + i);
    }
    var one = contacts.listContactMatches(f.ledger(), f.actor(), "李女士", 0);
    var two = contacts.listContactMatches(f.ledger(), f.actor(), "李女士", 1);
    assertEquals(21, one.totalCount());
    assertEquals(20, one.entries().size());
    assertEquals(1, two.entries().size());
    assertEquals(0, gifts.listGiftContacts(f.ledger(), f.actor()).size());

    var f2 = fixture();
    var legacyId = "legacy_" + UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO rental_contracts
            (id, ledger_id, home_id, landlord_name, starts_on, ends_on, monthly_rent_cents,
             deposit_cents, cycle, note, created_by, updated_by, created_at, updated_at)
        VALUES (?, ?, ?, '旧签约名称', ?, ?, 20000, 0, 'MONTHLY', '', ?, ?, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
        """,
        legacyId,
        f2.ledger(),
        f2.home(),
        START,
        START.plusMonths(1),
        f2.actor(),
        f2.actor());
    var legacy = rentals.getRentalContract(f2.ledger(), f2.actor(), legacyId);
    assertNull(legacy.landlordContactId());
    assertEquals("旧签约名称", legacy.landlordName());
    var replacement =
        gifts.createGiftContact(new CreateGiftContactCommand(f2.ledger(), f2.actor(), "新房东", ""));
    LocalDate next = legacy.endsOn().plusDays(1);
    var newContract =
        rentals.renewRentalContract(
            new RenewRentalContractCommand(
                f2.ledger(),
                f2.actor(),
                legacyId,
                legacy.endsOn(),
                "新房东",
                next,
                next.plusMonths(1),
                30000,
                0,
                RentalPaymentCycle.MONTHLY,
                null,
                "",
                null,
                30,
                replacement.id(),
                null));
    assertEquals(replacement.id(), newContract.landlordContactId());
    assertEquals(
        "旧签约名称", rentals.getRentalContract(f2.ledger(), f2.actor(), legacyId).landlordName());
    assertNull(rentals.getRentalContract(f2.ledger(), f2.actor(), legacyId).landlordContactId());
  }

  private Fixture fixture() {
    String ledger = "book_" + UUID.randomUUID();
    String actor = "ct-" + UUID.randomUUID();
    jdbc.update("INSERT INTO personal_ledgers (id, owner_id) VALUES (?, ?)", ledger, actor);
    var home = rentals.saveRentalHome(new SaveRentalHomeCommand(ledger, actor, null, "房屋", "", ""));
    return new Fixture(ledger, actor, home.id());
  }

  private RentalContractResult create(Fixture f, String name, String id, String distinction) {
    return rentals.createRentalContract(
        new CreateRentalContractCommand(
            f.ledger(),
            f.actor(),
            f.home(),
            name,
            START,
            START.plusMonths(1),
            250000,
            0,
            RentalPaymentCycle.MONTHLY,
            null,
            "",
            null,
            30,
            id,
            distinction));
  }

  private record Fixture(String ledger, String actor, String home) {}
}
