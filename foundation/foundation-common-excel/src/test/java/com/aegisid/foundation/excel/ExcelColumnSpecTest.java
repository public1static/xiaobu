package com.aegisid.foundation.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExcelColumnSpecTest {

  @Test
  void createsRequiredTextColumnWithStableKeyAndTitle() {
    ExcelColumnSpec column = ExcelColumnSpec.required("email", "邮箱", 10);

    assertThat(column.key()).isEqualTo("email");
    assertThat(column.title()).isEqualTo("邮箱");
    assertThat(column.required()).isTrue();
    assertThat(column.type()).isEqualTo(ExcelCellType.TEXT);
    assertThat(column.order()).isEqualTo(10);
  }

  @Test
  void rejectsBlankKeyAndTitle() {
    assertThatThrownBy(() -> ExcelColumnSpec.required("", "邮箱", 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("key");
    assertThatThrownBy(() -> ExcelColumnSpec.optional("email", " ", ExcelCellType.TEXT, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("title");
  }
}
