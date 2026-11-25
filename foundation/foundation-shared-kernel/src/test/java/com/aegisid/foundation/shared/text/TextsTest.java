package com.aegisid.foundation.shared.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextsTest {

  @Test
  void hasTextRequiresNonBlankText() {
    assertFalse(Texts.hasText(null));
    assertFalse(Texts.hasText(""));
    assertFalse(Texts.hasText("   "));
    assertTrue(Texts.hasText(" alpha "));
  }

  @Test
  void requireReturnsTrimmedText() {
    assertEquals("alpha", Texts.require(" alpha ", "name"));
  }

  @Test
  void requireRejectsNullOrBlankTextWithFieldName() {
    IllegalArgumentException nullException =
        assertThrows(IllegalArgumentException.class, () -> Texts.require(null, "name"));
    IllegalArgumentException blankException =
        assertThrows(IllegalArgumentException.class, () -> Texts.require(" ", "name"));

    assertEquals("name must not be blank", nullException.getMessage());
    assertEquals("name must not be blank", blankException.getMessage());
  }

  @Test
  void requireRejectsBlankFieldName() {
    assertThrows(IllegalArgumentException.class, () -> Texts.require("alpha", " "));
  }

  @Test
  void requireCanThrowCustomExceptionForMissingText() {
    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> Texts.require(" ", () -> new IllegalStateException("missing")));

    assertEquals("missing", exception.getMessage());
    assertEquals("alpha", Texts.require(" alpha ", () -> new IllegalStateException("missing")));
  }

  @Test
  void requireRawValidatesWithoutChangingTheValue() {
    assertEquals(" alpha ", Texts.requireRaw(" alpha ", "secret"));
    assertThrows(IllegalArgumentException.class, () -> Texts.requireRaw(null, "secret"));
    assertThrows(IllegalArgumentException.class, () -> Texts.requireRaw(" ", "secret"));
    assertThrows(IllegalArgumentException.class, () -> Texts.requireRaw("alpha", " "));
  }

  @Test
  void trimToNullReturnsNullForMissingTextAndTrimmedTextOtherwise() {
    assertNull(Texts.trimToNull(null));
    assertNull(Texts.trimToNull(""));
    assertNull(Texts.trimToNull("   "));
    assertEquals("alpha", Texts.trimToNull(" alpha "));
  }

  @Test
  void trimToEmptyReturnsEmptyForMissingTextAndTrimmedTextOtherwise() {
    assertEquals("", Texts.trimToEmpty(null));
    assertEquals("", Texts.trimToEmpty(""));
    assertEquals("", Texts.trimToEmpty("   "));
    assertEquals("alpha", Texts.trimToEmpty(" alpha "));
  }

  @Test
  void toAsciiSlugNormalizesTextAndSeparators() {
    assertEquals("hello-world", Texts.toAsciiSlug(" Hello, World! "));
    assertEquals("", Texts.toAsciiSlug(null));
    assertEquals("", Texts.toAsciiSlug("中文"));
  }
}
