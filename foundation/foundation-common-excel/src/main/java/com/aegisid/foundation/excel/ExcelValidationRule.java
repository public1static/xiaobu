package com.aegisid.foundation.excel;

import com.aegisid.foundation.shared.text.Texts;
import java.util.List;

public record ExcelValidationRule(
    String fieldKey,
    List<String> explicitValues,
    String referenceSheetName,
    int referenceColumnIndex,
    int maxRows) {

  public ExcelValidationRule {
    fieldKey = Texts.require(fieldKey, "fieldKey");
    explicitValues = explicitValues == null ? List.of() : List.copyOf(explicitValues);
    referenceSheetName = referenceSheetName == null ? "" : referenceSheetName;
    referenceColumnIndex = Math.max(referenceColumnIndex, 0);
    maxRows = maxRows <= 0 ? 10000 : maxRows;
  }

  public static ExcelValidationRule explicitValues(
      String fieldKey, List<String> values, int maxRows) {
    return new ExcelValidationRule(fieldKey, values, "", 0, maxRows);
  }

  public static ExcelValidationRule referenceValues(
      String fieldKey, String referenceSheetName, int referenceColumnIndex, int maxRows) {
    return new ExcelValidationRule(
        fieldKey, List.of(), referenceSheetName, referenceColumnIndex, maxRows);
  }

  public boolean explicit() {
    return !explicitValues.isEmpty();
  }
}
