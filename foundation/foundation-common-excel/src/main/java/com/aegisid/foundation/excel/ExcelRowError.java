package com.aegisid.foundation.excel;

public record ExcelRowError(
    int rowNumber, String fieldKey, String fieldTitle, String rawValue, String message) {}
