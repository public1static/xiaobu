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

  GiftTransactionExtensionHandler(GiftDetailRepository details) {
    this.details = details;
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
        .map(GiftDetailResult::from);
  }

  @Override
  public void validateRetained(TransactionExtensionContext context, TransactionExtensionData data) {
    if (!GiftBook.dailyFor(context.direction())
        .name()
        .equals(((GiftDetailResult) data).bookCode())) {
      throw new FoundationException(
          new ErrorCode("XIAOBU_GIFT_DETAIL_INVALID"), "收送方向已变更，请重新确认往来信息");
    }
  }

  @Override
  public void upsert(TransactionExtensionContext context, GiftDetailDraft detail) {
    details.save(
        context.ledgerId(),
        context.transactionId(),
        context.actorId(),
        new GiftDetail(
            GiftBook.dailyFor(context.direction()), detail.personName(), detail.reason()));
  }

  @Override
  public void remove(TransactionExtensionContext context) {
    details.remove(context.ledgerId(), context.transactionId(), context.actorId());
  }
}
