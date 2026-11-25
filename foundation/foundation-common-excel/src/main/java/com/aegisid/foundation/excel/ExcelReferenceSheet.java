package com.aegisid.foundation.excel;

import com.aegisid.foundation.shared.text.Texts;
import java.util.Comparator;
import java.util.List;

public record ExcelReferenceSheet(
    String sheetName, List<ExcelColumnSpec> columns, List<List<String>> rows) {

  public ExcelReferenceSheet {
    sheetName = Texts.require(sheetName, "sheetName");
    columns =
        columns == null
            ? List.of()
            : columns.stream().sorted(Comparator.comparingInt(ExcelColumnSpec::order)).toList();
    if (columns.isEmpty()) {
      throw new IllegalArgumentException("columns must not be empty");
    }
    rows =
        rows == null
            ? List.of()
            : rows.stream().map(row -> row == null ? List.<String>of() : List.copyOf(row)).toList();
  }
}
