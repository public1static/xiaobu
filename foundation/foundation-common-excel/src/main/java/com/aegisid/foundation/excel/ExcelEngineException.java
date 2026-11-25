package com.aegisid.foundation.excel;

public class ExcelEngineException extends RuntimeException {

  public ExcelEngineException(String message, Throwable cause) {
    super(message, cause);
  }

  public ExcelEngineException(String message) {
    super(message);
  }
}
