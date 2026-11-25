package com.aegisid.foundation.excel;

import java.util.List;

public record ExcelImportResult(
    String importType,
    int totalRows,
    int successRows,
    int failedRows,
    List<ExcelRowError> errors) {}
