package com.xiaobu.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransactionExtensionRegistryTest {
  @Test
  void aSecondBusinessRegistersWithoutChangingTheLedgerWorkflow() {
    var gift = new TestHandler("GIFT_EXAMPLE", "SPECIAL", true);
    var rent = new TestHandler("RENT_EXAMPLE", "SPECIAL", false);
    var registry = new TransactionExtensionRegistry(List.of(gift, rent));
    var context = context("SPECIAL");
    registry.apply(context, List.of(upsert(gift, "gift"), upsert(rent, "period")), true);
    registry.apply(context, List.of(), false);
    assertThat(registry.read(context))
        .extracting(TransactionExtensionResult::type)
        .containsExactly("GIFT_EXAMPLE", "RENT_EXAMPLE");
    assertThatThrownBy(() -> registry.apply(context("ORDINARY"), List.of(), false))
        .isInstanceOf(FoundationException.class);
    registry.apply(context("ORDINARY"), List.of(remove(gift), remove(rent)), false);
    assertThat(registry.read(context("ORDINARY"))).isEmpty();
  }

  @Test
  void rejectsMissingRequiredRepeatedOrUnknownTypesBeforeCallingHandlers() {
    var first = new TestHandler("EXAMPLE", "SPECIAL", true);
    var registry = new TransactionExtensionRegistry(List.of(first));
    var context = context("SPECIAL");
    assertThatThrownBy(() -> registry.apply(context, List.of(), true))
        .isInstanceOf(FoundationException.class);
    assertThatThrownBy(
            () -> registry.apply(context, List.of(upsert(first, "a"), upsert(first, "b")), true))
        .isInstanceOf(FoundationException.class);
    assertThatThrownBy(() ->
            registry.apply(
                context,
                List.of(
                    upsert(first, "a"),
                    new TransactionExtensionChange(
                        "UNKNOWN",
                        TransactionExtensionChange.Operation.UPSERT,
                        new TestDetail("ignored"))),
                true))
        .isInstanceOf(FoundationException.class);
    assertThat(first.value).isNull();
    assertThatThrownBy(() -> new TransactionExtensionRegistry(List.of(first, first)))
        .isInstanceOf(IllegalStateException.class);
  }

  private static TransactionExtensionContext context(String code) {
    return new TransactionExtensionContext(
        "ledger", "actor", "transaction", TransactionDirection.EXPENSE, code);
  }

  private static TransactionExtensionChange upsert(TestHandler handler, String value) {
    return new TransactionExtensionChange(
        handler.type(), TransactionExtensionChange.Operation.UPSERT, new TestDetail(value));
  }

  private static TransactionExtensionChange remove(TestHandler handler) {
    return new TransactionExtensionChange(
        handler.type(), TransactionExtensionChange.Operation.REMOVE, null);
  }

  private record TestDetail(String value) implements TransactionExtensionData {}

  private static class TestHandler implements TransactionExtensionHandler<TestDetail> {
    private final String type;
    private final String category;
    private final boolean required;
    private TestDetail value;

    TestHandler(String type, String category, boolean required) {
      this.type = type;
      this.category = category;
      this.required = required;
    }

    @Override
    public String type() {
      return type;
    }

    @Override
    public Class<TestDetail> draftType() {
      return TestDetail.class;
    }

    @Override
    public Optional<TransactionExtensionCapability> capability(
        TransactionDirection direction, String categoryCode) {
      return category.equals(categoryCode)
          ? Optional.of(new TransactionExtensionCapability(type, required))
          : Optional.empty();
    }

    @Override
    public Optional<TestDetail> read(TransactionExtensionContext context) {
      return Optional.ofNullable(value);
    }

    @Override
    public void upsert(TransactionExtensionContext context, TestDetail detail) {
      value = detail;
    }

    @Override
    public void remove(TransactionExtensionContext context) {
      value = null;
    }
  }
}
