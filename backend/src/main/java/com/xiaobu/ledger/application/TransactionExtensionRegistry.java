package com.xiaobu.ledger.application;

import com.aegisid.foundation.shared.error.ErrorCode;
import com.aegisid.foundation.shared.error.FoundationException;
import com.xiaobu.ledger.domain.TransactionDirection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TransactionExtensionRegistry {
  private static final ErrorCode INVALID = new ErrorCode("XIAOBU_TRANSACTION_EXTENSION_INVALID");

  private final List<TransactionExtensionHandler<?>> handlers;
  private final Map<String, TransactionExtensionHandler<?>> byType;

  public TransactionExtensionRegistry(List<TransactionExtensionHandler<?>> handlers) {
    this.handlers =
        handlers.stream().sorted(Comparator.comparing(TransactionExtensionHandler::type)).toList();
    byType =
        this.handlers.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    TransactionExtensionHandler::type, Function.identity()));
  }

  public Class<? extends TransactionExtensionData> draftType(String type) {
    return requireHandler(type).draftType();
  }

  public List<TransactionExtensionCapability> capabilities(
      TransactionDirection direction, String categoryCode) {
    return handlers.stream()
        .flatMap(handler -> handler.capability(direction, categoryCode).stream())
        .toList();
  }

  public List<TransactionExtensionResult> read(TransactionExtensionContext context) {
    return handlers.stream()
        .flatMap(handler ->
            handler.read(context).stream()
                .map(detail -> new TransactionExtensionResult(handler.type(), detail)))
        .toList();
  }

  public boolean allowsModification(TransactionExtensionContext context) {
    return handlers.stream().allMatch(handler -> handler.allowsModification(context));
  }

  public void apply(
      TransactionExtensionContext context,
      List<TransactionExtensionChange> changes,
      boolean creating) {
    var allowed =
        capabilities(context.direction(), context.categoryCode()).stream()
            .collect(Collectors.toMap(TransactionExtensionCapability::type, Function.identity()));
    var existing = new HashSet<String>();
    var previous =
        creating
            ? List.<TransactionExtensionResult>of()
            : handlers.stream()
                .flatMap(handler ->
                    handler.readForMutation(context).stream()
                        .map(detail -> new TransactionExtensionResult(handler.type(), detail)))
                .toList();
    previous.forEach(result -> existing.add(result.type()));
    var pending = new LinkedHashMap<String, TransactionExtensionChange>();
    for (var change : changes) {
      var handler = requireHandler(change.type());
      if (pending.putIfAbsent(change.type(), change) != null) {
        throw invalid("不能重复提交同一业务扩展");
      }
      if (change.operation() == TransactionExtensionChange.Operation.UPSERT) {
        if (!allowed.containsKey(change.type())
            || !handler.draftType().isInstance(change.detail())) {
          throw invalid("当前分类不支持这项业务或明细不完整");
        }
        existing.add(change.type());
      } else if (change.operation() == TransactionExtensionChange.Operation.REMOVE
          && !creating
          && change.detail() == null
          && existing.remove(change.type())) {
        // An explicit removal is allowed even when the new category no longer supports it.
      } else {
        throw invalid("无可移除的业务扩展或操作无效");
      }
    }
    if (!allowed.keySet().containsAll(existing)) throw invalid("更换分类前请确认如何处理现有业务明细");
    if (allowed.values().stream()
        .anyMatch(item -> item.required() && !existing.contains(item.type()))) {
      throw invalid("请补充当前分类需要的业务明细");
    }
    previous.stream()
        .filter(result -> !pending.containsKey(result.type()))
        .forEach(result -> requireHandler(result.type()).retain(context, result.detail()));
    pending.forEach((type, change) -> {
      var handler = requireHandler(type);
      if (change.operation() == TransactionExtensionChange.Operation.UPSERT) {
        handler.write(context, change.detail());
      } else {
        handler.remove(context);
      }
    });
  }

  private TransactionExtensionHandler<?> requireHandler(String type) {
    var handler = type == null ? null : byType.get(type);
    if (handler == null) throw invalid("不支持这项业务扩展");
    return handler;
  }

  private static FoundationException invalid(String message) {
    return new FoundationException(INVALID, message);
  }
}
