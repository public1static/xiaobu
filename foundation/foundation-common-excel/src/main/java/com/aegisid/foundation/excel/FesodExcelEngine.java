package com.aegisid.foundation.excel;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.context.AnalysisContext;
import org.apache.fesod.sheet.read.listener.ReadListener;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class FesodExcelEngine implements ExcelEngine {

  @Override
  public <R> List<R> read(InputStream input, ExcelImportDefinition<R> definition) {
    List<Map<Integer, String>> rows = new ArrayList<>();
    try {
      FesodSheet.read(
              input,
              new ReadListener<Map<Integer, String>>() {
                @Override
                public void invoke(Map<Integer, String> data, AnalysisContext context) {
                  if (rows.size() >= definition.maxRows() + 2) {
                    throw new RowLimitReachedException();
                  }
                  rows.add(data);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {}
              })
          .headRowNumber(0)
          .sheet()
          .doRead();
    } catch (RowLimitReachedException ignored) {
    } catch (RuntimeException exception) {
      throw new ExcelEngineException("excel workbook cannot be parsed", exception);
    }
    if (rows.isEmpty()) {
      return List.of();
    }
    List<R> mapped = new ArrayList<>();
    for (int index = 1; index < rows.size(); index++) {
      mapped.add(toRow(definition.rowType(), definition.columns(), rows.get(index)));
    }
    return mapped;
  }

  private static final class RowLimitReachedException extends RuntimeException {}

  @Override
  public <R> byte[] writeTemplate(ExcelImportDefinition<R> definition) {
    return writeTemplate(new ExcelTemplateDefinition<>(definition, List.of(), List.of()));
  }

  @Override
  public <R> byte[] writeTemplate(ExcelTemplateDefinition<R> definition) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Sheet importSheet = workbook.createSheet(definition.importDefinition().sheetName());
      writeHeaderRow(workbook, importSheet, definition.importDefinition().columns());
      writeReferenceSheets(workbook, definition.referenceSheets());
      addValidations(importSheet, definition);
      workbook.write(output);
      return output.toByteArray();
    } catch (Exception exception) {
      throw new ExcelEngineException("excel template cannot be written", exception);
    }
  }

  @Override
  public <R, Q> byte[] writeRows(ExcelExportDefinition<R, Q> definition, List<R> rows) {
    List<List<String>> outputRows = new ArrayList<>();
    outputRows.add(headers(definition.columns()));
    for (R row : rows) {
      outputRows.add(toValues(definition.columns(), row));
    }
    return writeRawRows(definition.sheetName(), outputRows);
  }

  @Override
  public byte[] writeFailures(ExcelImportDefinition<?> definition, List<ExcelRowError> errors) {
    return writeRawRows(definition.sheetName() + " Failures", failureRows(errors));
  }

  @Override
  public byte[] writeFailures(ExcelTemplateDefinition<?> definition, List<ExcelRowError> errors) {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      writeRows(
          workbook.createSheet(definition.importDefinition().sheetName() + " Failures"),
          failureRows(errors));
      writeReferenceSheets(workbook, definition.referenceSheets());
      workbook.write(output);
      return output.toByteArray();
    } catch (Exception exception) {
      throw new ExcelEngineException("excel failures cannot be written", exception);
    }
  }

  private static List<List<String>> failureRows(List<ExcelRowError> errors) {
    List<List<String>> rows = new ArrayList<>();
    rows.add(List.of("行号", "字段", "表头", "原始值", "错误原因"));
    for (ExcelRowError error : errors) {
      rows.add(
          List.of(
              Integer.toString(error.rowNumber()),
              safe(error.fieldKey()),
              safe(error.fieldTitle()),
              ExcelFormulaEscaper.escape(error.rawValue()),
              safe(error.message())));
    }
    return rows;
  }

  private static byte[] writeRawRows(String sheetName, List<List<String>> rows) {
    try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      FesodSheet.write(output).sheet(sheetName).doWrite(rows);
      return output.toByteArray();
    } catch (Exception exception) {
      throw new ExcelEngineException("excel workbook cannot be written", exception);
    }
  }

  private static void writeRows(Sheet sheet, List<List<String>> rows) {
    writeRows(sheet, 0, rows);
  }

  private static void writeRows(Sheet sheet, int startRowIndex, List<List<String>> rows) {
    for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
      Row row = sheet.createRow(startRowIndex + rowIndex);
      List<String> values = rows.get(rowIndex);
      for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
        Cell cell = row.createCell(columnIndex);
        cell.setCellValue(safe(values.get(columnIndex)));
      }
    }
  }

  private static void writeHeaderRow(
      XSSFWorkbook workbook, Sheet sheet, List<ExcelColumnSpec> columns) {
    Row row = sheet.createRow(0);
    for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
      Cell cell = row.createCell(columnIndex);
      setHeaderValue(workbook, cell, columns.get(columnIndex));
    }
  }

  private static void setHeaderValue(XSSFWorkbook workbook, Cell cell, ExcelColumnSpec column) {
    String title = safe(column.title());
    if (!column.required()) {
      cell.setCellValue(title);
      return;
    }
    XSSFRichTextString richText = new XSSFRichTextString(title + "*");
    if (!title.isEmpty()) {
      richText.applyFont(0, title.length(), workbook.createFont());
    }
    XSSFFont requiredFont = workbook.createFont();
    requiredFont.setColor(IndexedColors.RED.getIndex());
    richText.applyFont(title.length(), title.length() + 1, requiredFont);
    cell.setCellValue(richText);
  }

  private static void writeReferenceSheets(
      XSSFWorkbook workbook, List<ExcelReferenceSheet> referenceSheets) {
    for (ExcelReferenceSheet referenceSheet : referenceSheets) {
      Sheet sheet = workbook.createSheet(referenceSheet.sheetName());
      writeHeaderRow(workbook, sheet, referenceSheet.columns());
      writeRows(sheet, 1, referenceSheet.rows());
    }
  }

  private static void addValidations(Sheet sheet, ExcelTemplateDefinition<?> definition) {
    DataValidationHelper helper = sheet.getDataValidationHelper();
    for (ExcelValidationRule validation : definition.validations()) {
      int columnIndex =
          columnIndex(definition.importDefinition().columns(), validation.fieldKey()).orElse(-1);
      if (columnIndex < 0) {
        continue;
      }
      DataValidationConstraint constraint =
          constraint(helper, validation, definition.referenceSheets());
      if (constraint == null) {
        continue;
      }
      CellRangeAddressList addressList =
          new CellRangeAddressList(1, validation.maxRows(), columnIndex, columnIndex);
      DataValidation dataValidation = helper.createValidation(constraint, addressList);
      dataValidation.setSuppressDropDownArrow(false);
      dataValidation.setShowErrorBox(true);
      sheet.addValidationData(dataValidation);
    }
  }

  private static DataValidationConstraint constraint(
      DataValidationHelper helper,
      ExcelValidationRule validation,
      List<ExcelReferenceSheet> referenceSheets) {
    if (validation.explicit()) {
      return helper.createExplicitListConstraint(
          validation.explicitValues().toArray(String[]::new));
    }
    Optional<ExcelReferenceSheet> referenceSheet =
        referenceSheets.stream()
            .filter(sheet -> sheet.sheetName().equals(validation.referenceSheetName()))
            .findFirst();
    if (referenceSheet.isEmpty() || referenceSheet.get().rows().isEmpty()) {
      return null;
    }
    int columnNumber = validation.referenceColumnIndex() + 1;
    String column = columnName(columnNumber);
    int endRow = referenceSheet.get().rows().size() + 1;
    String formula =
        "'"
            + referenceSheet.get().sheetName().replace("'", "''")
            + "'!$"
            + column
            + "$2:$"
            + column
            + "$"
            + endRow;
    return helper.createFormulaListConstraint(formula);
  }

  private static Optional<Integer> columnIndex(List<ExcelColumnSpec> columns, String fieldKey) {
    for (int index = 0; index < columns.size(); index++) {
      if (columns.get(index).key().equals(fieldKey)) {
        return Optional.of(index);
      }
    }
    return Optional.empty();
  }

  private static String columnName(int number) {
    StringBuilder name = new StringBuilder();
    int value = number;
    while (value > 0) {
      int remainder = (value - 1) % 26;
      name.insert(0, (char) ('A' + remainder));
      value = (value - 1) / 26;
    }
    return name.toString();
  }

  private static List<String> headers(List<ExcelColumnSpec> columns) {
    return columns.stream().map(ExcelColumnSpec::title).toList();
  }

  private static <R> R toRow(
      Class<R> rowType, List<ExcelColumnSpec> columns, Map<Integer, String> values) {
    Map<String, String> byKey = new LinkedHashMap<>();
    for (int index = 0; index < columns.size(); index++) {
      byKey.put(columns.get(index).key(), safe(values.get(index)));
    }
    try {
      var constructor = rowType.getDeclaredConstructors()[0];
      constructor.setAccessible(true);
      Object[] args =
          rowType.isRecord()
              ? recordArguments(rowType, byKey)
              : List.copyOf(byKey.values()).toArray();
      return rowType.cast(constructor.newInstance(args));
    } catch (InstantiationException
        | IllegalAccessException
        | InvocationTargetException exception) {
      throw new ExcelEngineException("excel row cannot be mapped", exception);
    }
  }

  private static Object[] recordArguments(Class<?> rowType, Map<String, String> byKey) {
    RecordComponent[] components = rowType.getRecordComponents();
    Object[] args = new Object[components.length];
    for (int index = 0; index < components.length; index++) {
      args[index] = byKey.getOrDefault(components[index].getName(), "");
    }
    return args;
  }

  private static List<String> toValues(List<ExcelColumnSpec> columns, Object row) {
    Map<String, PropertyDescriptor> properties = properties(row.getClass());
    List<String> values = new ArrayList<>();
    for (ExcelColumnSpec column : columns) {
      Object value = readValue(row, column.key(), properties);
      values.add(ExcelFormulaEscaper.escape(value == null ? "" : String.valueOf(value)));
    }
    return values;
  }

  private static Object readValue(
      Object row, String key, Map<String, PropertyDescriptor> properties) {
    if (row.getClass().isRecord()) {
      for (RecordComponent component : row.getClass().getRecordComponents()) {
        if (component.getName().equals(key)) {
          try {
            return component.getAccessor().invoke(row);
          } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new ExcelEngineException("excel row cannot be read", exception);
          }
        }
      }
    }
    PropertyDescriptor property = properties.get(key);
    if (property == null || property.getReadMethod() == null) {
      return "";
    }
    try {
      return property.getReadMethod().invoke(row);
    } catch (IllegalAccessException | InvocationTargetException exception) {
      throw new ExcelEngineException("excel row cannot be read", exception);
    }
  }

  private static Map<String, PropertyDescriptor> properties(Class<?> type) {
    try {
      Map<String, PropertyDescriptor> descriptors = new LinkedHashMap<>();
      for (PropertyDescriptor descriptor :
          Introspector.getBeanInfo(type).getPropertyDescriptors()) {
        descriptors.put(descriptor.getName(), descriptor);
      }
      return descriptors;
    } catch (IntrospectionException exception) {
      throw new ExcelEngineException("excel row properties cannot be inspected", exception);
    }
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
