package com.aegisid.foundation.data.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.Instant;

/** Converts {@link Instant} values to a JDBC-friendly timestamp representation. */
@Converter(autoApply = true)
public final class InstantAttributeConverter implements AttributeConverter<Instant, Timestamp> {

  @Override
  public Timestamp convertToDatabaseColumn(Instant attribute) {
    return attribute == null ? null : Timestamp.from(attribute);
  }

  @Override
  public Instant convertToEntityAttribute(Timestamp dbData) {
    return dbData == null ? null : dbData.toInstant();
  }
}
