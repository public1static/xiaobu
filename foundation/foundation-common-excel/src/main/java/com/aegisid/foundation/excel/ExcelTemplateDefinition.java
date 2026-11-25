package com.aegisid.foundation.excel;

import java.util.List;
import java.util.Objects;

public record ExcelTemplateDefinition<R>(
    ExcelImportDefinition<R> importDefinition,
    List<ExcelReferenceSheet> referenceSheets,
    List<ExcelValidationRule> validations) {

  public ExcelTemplateDefinition {
    importDefinition =
        Objects.requireNonNull(importDefinition, "importDefinition must not be null");
    referenceSheets = referenceSheets == null ? List.of() : List.copyOf(referenceSheets);
    validations = validations == null ? List.of() : List.copyOf(validations);
  }
}
