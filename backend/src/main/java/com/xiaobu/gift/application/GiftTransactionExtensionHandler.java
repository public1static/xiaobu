package com.xiaobu.gift.application;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.gift.domain.GiftBook;
import com.xiaobu.gift.domain.GiftDetail;
import com.xiaobu.ledger.application.TransactionExtensionCapability;
import com.xiaobu.ledger.application.TransactionExtensionContext;
import com.xiaobu.ledger.application.TransactionExtensionData;
import com.xiaobu.ledger.application.TransactionExtensionHandler;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class GiftTransactionExtensionHandler implements TransactionExtensionHandler<GiftDetailDraft> {
  private static final String TYPE = "GIFT";
  private final GiftDetailRepository details;
  private final GiftDirectoryApplicationService directory;
  private final LedgerContactApplicationService contacts;

  GiftTransactionExtensionHandler(
      GiftDetailRepository details,
      GiftDirectoryApplicationService directory,
      LedgerContactApplicationService contacts) {
    this.details = details;
    this.directory = directory;
    this.contacts = contacts;
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public Class<GiftDetailDraft> draftType() {
    return GiftDetailDraft.class;
  }

  @Override
  public Optional<TransactionExtensionCapability> capability(
      TransactionDirection direction, String categoryCode) {
    String expected = direction == TransactionDirection.INCOME ? "GIFT_RECEIVED" : "GIFT_SENT";
    return expected.equals(categoryCode)
        ? Optional.of(new TransactionExtensionCapability(TYPE, true))
        : Optional.empty();
  }

  @Override
  public Optional<GiftDetailResult> read(TransactionExtensionContext context) {
    return details
        .findActive(context.ledgerId(), context.transactionId())
        .map(detail ->
            GiftDetailResult.from(
                detail,
                detail.bookId() == null
                    ? detail.book().displayName()
                    : directory.requireBook(context.ledgerId(), detail.bookId()).name()));
  }

  @Override
  public boolean allowsModification(TransactionExtensionContext context) {
    return details
        .findActive(context.ledgerId(), context.transactionId())
        .map(detail ->
            detail.bookId() == null
                || !directory.requireBook(context.ledgerId(), detail.bookId()).archived())
        .orElse(true);
  }

  @Override
  public void retain(TransactionExtensionContext context, TransactionExtensionData data) {
    if (!GiftBook.dailyFor(context.direction())
        .name()
        .equals(((GiftDetailResult) data).bookCode())) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_GIFT_DETAIL_INVALID"), "收送方向已变更，请重新确认往来信息");
    }
  }

  @Override
  public void upsert(TransactionExtensionContext context, GiftDetailDraft detail) {
    String bookId = detail.bookId();
    if (bookId != null) {
      var book = directory.requireBook(context.ledgerId(), bookId);
      if (book.direction() != context.direction()) {
        throw invalid("请选择同方向的礼单");
      }
      if (book.archived()) {
        throw invalid("礼单已归档，请重新开放后再登记");
      }
    }
    if (detail.contactId() != null && Boolean.TRUE.equals(detail.createContact())) {
      throw invalid("请在选择已有亲友和新增亲友之间选一种");
    }
    var contact =
        detail.contactId() != null
            ? contacts.selectGiftContact(context.ledgerId(), context.actorId(), detail.contactId())
            : Boolean.TRUE.equals(detail.createContact())
                ? contacts.registerGiftContact(
                    new CreateGiftContactCommand(
                        context.ledgerId(),
                        context.actorId(),
                        detail.personName(),
                        detail.distinction()))
                : null;
    details.save(
        context.ledgerId(),
        context.transactionId(),
        context.actorId(),
        new GiftDetail(
            GiftBook.dailyFor(context.direction()),
            bookId,
            contact == null ? null : contact.id(),
            contact == null ? detail.personName() : contact.name(),
            contact == null ? null : contact.distinction(),
            detail.reason()));
  }

  @Override
  public void remove(TransactionExtensionContext context) {
    details.remove(context.ledgerId(), context.transactionId(), context.actorId());
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(new ErrorCode("XIAOBU_GIFT_DETAIL_INVALID"), message);
  }
}
