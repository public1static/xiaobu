package com.aegisid.foundation.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.junit.jupiter.api.Test;

class FesodExcelEngineTest {

  record UserImportRow(String email, String username, String password) {}

  record UserExportRow(String email, String username, String password, String status) {}

  @Test
  void writesTemplateWithHeaders() {
    FesodExcelEngine engine = new FesodExcelEngine();

    byte[] bytes = engine.writeTemplate(userDefinition());

    assertThat(bytes).isNotEmpty();
  }

  @Test
  void writesRequiredTemplateHeadersWithRedAsterisk() throws Exception {
    FesodExcelEngine engine = new FesodExcelEngine();
    ExcelImportDefinition<UserImportRow> definition =
        new ExcelImportDefinition<>(
            "user-import",
            "Users",
            UserImportRow.class,
            List.of(
                ExcelColumnSpec.required("email", "邮箱", 10),
                ExcelColumnSpec.optional("username", "用户名", ExcelCellType.TEXT, 20)),
            5000,
            true);

    byte[] bytes = engine.writeTemplate(definition);

    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      assertRequiredHeader(workbook.getSheet("Users"), 0, "邮箱");
      assertThat(workbook.getSheet("Users").getRow(0).getCell(1).getStringCellValue())
          .isEqualTo("用户名");
    }
  }

  @Test
  void writesAndReadsRowsByProjectColumnSpec() {
    FesodExcelEngine engine = new FesodExcelEngine();
    ExcelExportDefinition<UserImportRow, Void> exportDefinition =
        new ExcelExportDefinition<>(
            "user-export", "Users", UserImportRow.class, userDefinition().columns(), 5000);

    byte[] bytes =
        engine.writeRows(
            exportDefinition,
            List.of(new UserImportRow("alice@example.com", "alice", "=Secret123")));
    List<UserImportRow> rows = engine.read(new ByteArrayInputStream(bytes), userDefinition());

    assertThat(rows).extracting(UserImportRow::email).containsExactly("alice@example.com");
    assertThat(rows).extracting(UserImportRow::password).containsExactly("'=Secret123");
  }

  @Test
  void readsRecordRowsWhenImportColumnsAreSubsetOfRecordComponents() {
    FesodExcelEngine engine = new FesodExcelEngine();
    ExcelExportDefinition<UserExportRow, Void> exportDefinition =
        new ExcelExportDefinition<>(
            "user-export", "Users", UserExportRow.class, userDefinition().columns(), 5000);

    byte[] bytes =
        engine.writeRows(
            exportDefinition,
            List.of(new UserExportRow("alice@example.com", "alice", "Secret123", "ACTIVE")));
    List<UserExportRow> rows =
        engine.read(new ByteArrayInputStream(bytes), userExportImportDefinition());

    assertThat(rows)
        .containsExactly(new UserExportRow("alice@example.com", "alice", "Secret123", ""));
  }

  @Test
  void stopsReadingAfterConfiguredRowLimitPlusOne() {
    FesodExcelEngine engine = new FesodExcelEngine();
    ExcelExportDefinition<UserImportRow, Void> exportDefinition =
        new ExcelExportDefinition<>(
            "user-export", "Users", UserImportRow.class, userDefinition().columns(), 5000);
    List<UserImportRow> sourceRows =
        java.util.stream.IntStream.range(0, 5002)
            .mapToObj(
                index ->
                    new UserImportRow("user" + index + "@example.com", "user" + index, "Secret123"))
            .toList();

    byte[] bytes = engine.writeRows(exportDefinition, sourceRows);
    List<UserImportRow> rows = engine.read(new ByteArrayInputStream(bytes), userDefinition());

    assertThat(rows).hasSize(5001);
  }

  @Test
  void writesTemplateWithReferenceSheetsAndValidationRules() throws Exception {
    FesodExcelEngine engine = new FesodExcelEngine();
    ExcelTemplateDefinition<UserImportRow> definition =
        new ExcelTemplateDefinition<>(
            userDefinition(),
            List.of(
                new ExcelReferenceSheet(
                    "部门列表",
                    List.of(
                        ExcelColumnSpec.required("code", "部门编码", 10),
                        ExcelColumnSpec.optional("name", "部门名称", ExcelCellType.TEXT, 20)),
                    List.of(List.of("eng", "Engineering")))),
            List.of(
                ExcelValidationRule.explicitValues("username", List.of("alice", "bob"), 100),
                ExcelValidationRule.referenceValues("email", "部门列表", 0, 100)));

    byte[] bytes = engine.writeTemplate(definition);

    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
      assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Users");
      assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("部门列表");
      assertThat(workbook.getSheet("Users").getRow(0).getCell(0).getStringCellValue())
          .isEqualTo("邮箱*");
      assertThat(workbook.getSheet("部门列表").getRow(0).getCell(0).getStringCellValue())
          .isEqualTo("部门编码*");
      assertThat(workbook.getSheet("部门列表").getRow(0).getCell(1).getStringCellValue())
          .isEqualTo("部门名称");
      assertThat(workbook.getSheet("部门列表").getRow(1).getCell(0).getStringCellValue())
          .isEqualTo("eng");
      Sheet users = workbook.getSheet("Users");
      List<? extends DataValidation> validations = users.getDataValidations();
      assertThat(validations).hasSize(2);
      assertThat(validations.getFirst().getValidationConstraint().getExplicitListValues())
          .containsExactly("alice", "bob");
      assertThat(validations.get(1).getValidationConstraint().getFormula1())
          .isEqualTo("'部门列表'!$A$2:$A$2");
    }
  }

  @Test
  void writesFailuresWithReferenceSheets() throws Exception {
    FesodExcelEngine engine = new FesodExcelEngine();
    ExcelTemplateDefinition<UserImportRow> definition =
        new ExcelTemplateDefinition<>(
            userDefinition(),
            List.of(
                new ExcelReferenceSheet(
                    "部门列表",
                    List.of(
                        ExcelColumnSpec.required("code", "部门编码", 10),
                        ExcelColumnSpec.optional("name", "部门名称", ExcelCellType.TEXT, 20)),
                    List.of(List.of("eng", "Engineering")))),
            List.of());

    byte[] bytes =
        engine.writeFailures(
            definition, List.of(new ExcelRowError(2, "email", "邮箱", "bad@example.com", "邮箱不存在")));

    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
      assertThat(workbook.getSheetAt(0).getSheetName()).isEqualTo("Users Failures");
      assertThat(workbook.getSheetAt(1).getSheetName()).isEqualTo("部门列表");
      assertThat(workbook.getSheet("Users Failures").getRow(1).getCell(4).getStringCellValue())
          .isEqualTo("邮箱不存在");
      assertThat(workbook.getSheet("部门列表").getRow(1).getCell(0).getStringCellValue())
          .isEqualTo("eng");
    }
  }

  private static void assertRequiredHeader(Sheet sheet, int columnIndex, String title) {
    XSSFRichTextString richText =
        (XSSFRichTextString) sheet.getRow(0).getCell(columnIndex).getRichStringCellValue();
    assertThat(richText.getString()).isEqualTo(title + "*");
    assertThat(richText.numFormattingRuns()).isGreaterThanOrEqualTo(2);
    assertThat(richText.getIndexOfFormattingRun(1)).isEqualTo(title.length());
    XSSFFont starFont = richText.getFontOfFormattingRun(1);
    assertThat(starFont).isNotNull();
    assertThat(starFont.getColor()).isEqualTo(IndexedColors.RED.getIndex());
  }

  private static ExcelImportDefinition<UserImportRow> userDefinition() {
    return new ExcelImportDefinition<>(
        "user-import",
        "Users",
        UserImportRow.class,
        List.of(
            ExcelColumnSpec.required("email", "邮箱", 10),
            ExcelColumnSpec.required("username", "用户名", 20),
            ExcelColumnSpec.required("password", "初始密码", 30)),
        5000,
        true);
  }

  private static ExcelImportDefinition<UserExportRow> userExportImportDefinition() {
    return new ExcelImportDefinition<>(
        "user-import", "Users", UserExportRow.class, userDefinition().columns(), 5000, true);
  }
}
