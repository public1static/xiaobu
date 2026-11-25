package com.aegisid.foundation.excel;

public final class ExcelFormulaEscaper {
  private ExcelFormulaEscaper() {}

  public static String escape(String value) {
    if (value == null || value.isBlank()) {
      return value == null ? "" : value;
    }
    char first = value.charAt(0);
    return first == '=' || first == '+' || first == '-' || first == '@' ? "'" + value : value;
  }
}
