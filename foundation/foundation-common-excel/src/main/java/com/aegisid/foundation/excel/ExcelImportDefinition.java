package com.aegisid.foundation.excel;

import com.aegisid.foundation.shared.text.Texts;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ExcelImportDefinition<R>(
    String type,
    String sheetName,
    Class<R> rowType,
    List<ExcelColumnSpec> columns,
    int maxRows,
    boolean partialSuccessAllowed) {

  public ExcelImportDefinition {
    type = Texts.require(type, "type");
    sheetName = Texts.require(sheetName, "sheetName");
    rowType = Objects.requireNonNull(rowType, "rowType must not be null");
    columns =
        columns == null
            ? List.of()
            : columns.stream().sorted(Comparator.comparingInt(ExcelColumnSpec::order)).toList();
    if (columns.isEmpty()) {
      throw new IllegalArgumentException("columns must not be empty");
    }
    maxRows = maxRows <= 0 ? 5000 : maxRows;
  }
}
