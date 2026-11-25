package com.aegisid.foundation.excel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExcelFormulaEscaperTest {

  @Test
  void prefixesFormulaLikeTextWithSingleQuote() {
    assertThat(ExcelFormulaEscaper.escape("=cmd")).isEqualTo("'=cmd");
    assertThat(ExcelFormulaEscaper.escape("+SUM(A1:A2)")).isEqualTo("'+SUM(A1:A2)");
    assertThat(ExcelFormulaEscaper.escape("-10")).isEqualTo("'-10");
    assertThat(ExcelFormulaEscaper.escape("@HYPERLINK")).isEqualTo("'@HYPERLINK");
  }

  @Test
  void leavesNormalAndBlankTextUnchanged() {
    assertThat(ExcelFormulaEscaper.escape("alice@example.com")).isEqualTo("alice@example.com");
    assertThat(ExcelFormulaEscaper.escape("")).isEqualTo("");
    assertThat(ExcelFormulaEscaper.escape(null)).isEqualTo("");
  }
}
