package com.xiaobu.ledger.interfaces;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.application.TransactionExtensionChange;
import com.xiaobu.ledger.application.TransactionExtensionData;
import com.xiaobu.ledger.application.TransactionExtensionRegistry;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
@Profile("local-expense | wechat-preview")
class TransactionExtensionWebMapper {
  private final JsonMapper json;
  private final TransactionExtensionRegistry registry;

  TransactionExtensionWebMapper(JsonMapper json, TransactionExtensionRegistry registry) {
    this.json = json;
    this.registry = registry;
  }

  List<TransactionExtensionChange> toChanges(List<TransactionExtensionRequest> requests) {
    return requests == null ? List.of() : requests.stream().map(this::toChange).toList();
  }

  private TransactionExtensionChange toChange(TransactionExtensionRequest request) {
    var expectedType = registry.draftType(request.type());
    var input = request.detail();
    TransactionExtensionData detail = null;
    if (input != null && !input.isNull()) {
      if (!input.isObject()) throw invalidDetail();
      try {
        detail = json.treeToValue(input, expectedType);
      } catch (JacksonException failure) {
        throw invalidDetail();
      }
    }
    return new TransactionExtensionChange(request.type(), request.operation(), detail);
  }

  private static FoundationException invalidDetail() {
    return new FoundationException(
        new ErrorCode("XIAOBU_TRANSACTION_EXTENSION_INVALID"), "业务明细格式不正确");
  }
}
