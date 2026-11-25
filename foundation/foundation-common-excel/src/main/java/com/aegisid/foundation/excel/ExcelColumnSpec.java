package com.aegisid.foundation.excel;

import com.aegisid.foundation.shared.text.Texts;
import java.util.Objects;

public record ExcelColumnSpec(
    String key,
    String title,
    boolean required,
    ExcelCellType type,
    int order,
    String example,
    String description) {

  public ExcelColumnSpec {
    key = Texts.require(key, "key");
    title = Texts.require(title, "title");
    type = Objects.requireNonNull(type, "type must not be null");
    example = example == null ? "" : example;
    description = description == null ? "" : description;
  }

  public static ExcelColumnSpec required(String key, String title, int order) {
    return new ExcelColumnSpec(key, title, true, ExcelCellType.TEXT, order, "", "");
  }

  public static ExcelColumnSpec optional(String key, String title, ExcelCellType type, int order) {
    return new ExcelColumnSpec(key, title, false, type, order, "", "");
  }
}
