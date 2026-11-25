package com.aegisid.foundation.excel;

import java.util.List;

public record RowImportResult(boolean successful, List<ExcelRowError> errors) {

  public static RowImportResult success() {
    return new RowImportResult(true, List.of());
  }

  public static RowImportResult failed(ExcelRowError error) {
    return new RowImportResult(false, List.of(error));
  }

  public static RowImportResult failed(List<ExcelRowError> errors) {
    return new RowImportResult(false, errors);
  }
}
