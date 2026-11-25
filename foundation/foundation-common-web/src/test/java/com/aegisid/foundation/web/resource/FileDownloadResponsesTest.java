package com.aegisid.foundation.web.resource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class FileDownloadResponsesTest {

  @Test
  void buildsExcelAttachmentResponse() {
    byte[] content = "xlsx".getBytes();

    ResponseEntity<byte[]> response = FileDownloadResponses.excel("users.xlsx", content);

    assertTrue(response.getStatusCode().is2xxSuccessful());
    assertArrayEquals(content, response.getBody());
    assertEquals(
        MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        response.getHeaders().getContentType());
    assertEquals(
        "attachment; filename=\"users.xlsx\"",
        response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
  }

  @Test
  void buildsCsvAttachmentResponse() {
    String content = "id,name\n1,Alice\n";

    ResponseEntity<byte[]> response = FileDownloadResponses.csv("audit.csv", content);

    assertTrue(response.getStatusCode().is2xxSuccessful());
    assertArrayEquals(
        content.getBytes(java.nio.charset.StandardCharsets.UTF_8), response.getBody());
    assertEquals(
        MediaType.parseMediaType("text/csv;charset=UTF-8"), response.getHeaders().getContentType());
    assertEquals(
        "attachment; filename=\"audit.csv\"",
        response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
  }

  @Test
  void sanitizesAttachmentFileNameBeforeWritingHeader() {
    ResponseEntity<byte[]> response =
        FileDownloadResponses.csv("../audit\r\nX-Injected: yes.csv", "id\n");

    assertEquals(
        "attachment; filename=\"audit-X-Injected_ yes.csv\"",
        response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
  }
}
