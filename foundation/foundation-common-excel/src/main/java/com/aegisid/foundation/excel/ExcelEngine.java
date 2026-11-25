package com.aegisid.foundation.excel;

import java.io.InputStream;
import java.util.List;

public interface ExcelEngine {

  <R> List<R> read(InputStream input, ExcelImportDefinition<R> definition);

  <R> byte[] writeTemplate(ExcelImportDefinition<R> definition);

  default <R> byte[] writeTemplate(ExcelTemplateDefinition<R> definition) {
    return writeTemplate(definition.importDefinition());
  }

  <R, Q> byte[] writeRows(ExcelExportDefinition<R, Q> definition, List<R> rows);

  byte[] writeFailures(ExcelImportDefinition<?> definition, List<ExcelRowError> errors);

  default byte[] writeFailures(ExcelTemplateDefinition<?> definition, List<ExcelRowError> errors) {
    return writeFailures(definition.importDefinition(), errors);
  }
}
