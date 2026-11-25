package com.aegisid.foundation.web.resource;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class FileDownloadResponses {

  public static final MediaType EXCEL_MEDIA_TYPE =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private FileDownloadResponses() {}

  public static ResponseEntity<byte[]> excel(String fileName, byte[] content) {
    return attachment(fileName, EXCEL_MEDIA_TYPE, content);
  }

  public static ResponseEntity<byte[]> csv(String fileName, String content) {
    return attachment(
        fileName,
        new MediaType("text", "csv", StandardCharsets.UTF_8),
        content.getBytes(StandardCharsets.UTF_8));
  }

  public static ResponseEntity<byte[]> attachment(
      String fileName, MediaType mediaType, byte[] content) {
    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(safeFileName(fileName)).build().toString())
        .body(content);
  }

  private static String safeFileName(String fileName) {
    String normalized = fileName == null ? "" : fileName.trim().replace('\\', '/');
    int separator = normalized.lastIndexOf('/');
    String baseName = separator >= 0 ? normalized.substring(separator + 1) : normalized;
    StringBuilder safe = new StringBuilder();
    boolean previousDash = false;
    for (int index = 0; index < baseName.length(); index++) {
      char character = baseName.charAt(index);
      if (Character.isISOControl(character)) {
        if (!previousDash) {
          safe.append('-');
          previousDash = true;
        }
      } else {
        safe.append(safeFileNameCharacter(character));
        previousDash = false;
      }
    }
    String result = safe.toString().trim();
    while (result.startsWith(".")) {
      result = result.substring(1);
    }
    return result.isBlank() ? "download" : result;
  }

  private static char safeFileNameCharacter(char character) {
    return switch (character) {
      case ':', '"', '\'', ';', '/', '\\' -> '_';
      default -> character;
    };
  }
}
