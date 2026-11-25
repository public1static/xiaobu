package com.aegisid.foundation.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aegisid.foundation.data.id.IdGenerator;
import com.aegisid.foundation.data.id.IdPrefix;
import com.aegisid.foundation.data.id.PrefixedIdGenerator;
import com.aegisid.foundation.data.id.UuidV7EntityIdGenerator;
import com.aegisid.foundation.data.jpa.AuditableEntity;
import com.aegisid.foundation.data.jpa.InstantAttributeConverter;
import com.aegisid.foundation.data.jpa.JpaPageSupport;
import com.aegisid.foundation.data.jpa.SoftDeletableEntity;
import com.aegisid.foundation.shared.id.TypedId;
import com.aegisid.foundation.shared.page.PageQuery;
import com.aegisid.foundation.shared.page.PageResult;
import com.aegisid.foundation.shared.page.SortDirection;
import com.aegisid.foundation.shared.page.SortOrder;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class DataFoundationTest {

  @Test
  void prefixedIdGeneratorCreatesIdWithSpecifiedPrefix() {
    IdGenerator generator = new PrefixedIdGenerator("usr");

    String id = generator.generateId();

    assertTrue(id.startsWith("usr_"));
    assertTrue(id.length() > "usr_".length());
  }

  @Test
  void prefixedIdGeneratorRejectsBlankPrefix() {
    assertThrows(IllegalArgumentException.class, () -> new PrefixedIdGenerator(" "));
  }

  @Test
  void generatedIdUsesUuidV7() {
    String id = new UuidV7EntityIdGenerator().next(new IdPrefix("ses"));

    assertEquals(7, java.util.UUID.fromString(id.substring("ses_".length())).version());
  }

  @Test
  void generatedStringIdCanBeWrappedByTypedId() {
    IdGenerator generator = new PrefixedIdGenerator("org");

    TypedId<OrganizationMarker> typedId = new TypedId<>(generator.generateId());

    assertTrue(typedId.value().startsWith("org_"));
  }

  @Test
  void idPrefixRejectsUnregisteredShape() {
    assertThrows(IllegalArgumentException.class, () -> new IdPrefix("risk_event"));
    assertThrows(IllegalArgumentException.class, () -> new IdPrefix("a"));
    assertThrows(IllegalArgumentException.class, () -> new IdPrefix("prefix-is-too-long"));
  }

  @Test
  void auditableEntityExposesCreatedAtAndUpdatedAt() {
    TestAuditableEntity entity = new TestAuditableEntity();
    Instant createdAt = Instant.parse("2026-04-25T01:02:03.456Z");
    Instant updatedAt = Instant.parse("2026-04-25T02:03:04.567Z");

    entity.assignCreatedAt(createdAt);
    entity.assignUpdatedAt(updatedAt);

    assertEquals(createdAt, entity.getCreatedAt());
    assertEquals(updatedAt, entity.getUpdatedAt());
  }

  @Test
  void softDeletableEntityExposesDeletedAt() {
    TestSoftDeletableEntity entity = new TestSoftDeletableEntity();
    Instant deletedAt = Instant.parse("2026-04-25T03:04:05.678Z");

    entity.assignDeletedAt(deletedAt);

    assertEquals(deletedAt, entity.getDeletedAt());
  }

  @Test
  void instantAttributeConverterPreservesUtcInstant() {
    InstantAttributeConverter converter = new InstantAttributeConverter();
    Instant instant = Instant.parse("2026-04-25T04:05:06.789Z");

    Timestamp databaseValue = converter.convertToDatabaseColumn(instant);
    Instant restored = converter.convertToEntityAttribute(databaseValue);

    assertNotNull(databaseValue);
    assertEquals(instant, restored);
  }

  @Test
  void jpaPageSupportConvertsExternalPageQueryToSpringPageRequest() {
    PageQuery query = new PageQuery(2, 10, List.of(new SortOrder("name", SortDirection.ASC)));

    PageRequest pageRequest =
        JpaPageSupport.toPageRequest(query, Sort.by(Sort.Direction.DESC, "createdAt"));

    assertEquals(1, pageRequest.getPageNumber());
    assertEquals(10, pageRequest.getPageSize());
    assertEquals(Sort.Direction.ASC, pageRequest.getSort().getOrderFor("name").getDirection());
  }

  @Test
  void jpaPageSupportUsesDefaultSortWhenQueryHasNoSort() {
    PageQuery query = new PageQuery(1, 20);

    PageRequest pageRequest =
        JpaPageSupport.toPageRequest(query, Sort.by(Sort.Direction.DESC, "createdAt"));

    assertEquals(
        Sort.Direction.DESC, pageRequest.getSort().getOrderFor("createdAt").getDirection());
  }

  @Test
  void jpaPageSupportConvertsSpringPageToPageResultWithMappedItems() {
    PageQuery query = new PageQuery(2, 2, List.of(new SortOrder("name", SortDirection.DESC)));
    PageImpl<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(1, 2), 5);

    PageResult<String> result = JpaPageSupport.toPageResult(page, query, value -> "item-" + value);

    assertEquals(List.of("item-1", "item-2"), result.items());
    assertEquals(2, result.page());
    assertEquals(2, result.size());
    assertEquals(5, result.totalElements());
    assertEquals(3, result.totalPages());
    assertEquals(query.sort(), result.sort());
  }

  private interface OrganizationMarker {}

  private static final class TestAuditableEntity extends AuditableEntity {

    void assignCreatedAt(Instant createdAt) {
      setCreatedAt(createdAt);
    }

    void assignUpdatedAt(Instant updatedAt) {
      setUpdatedAt(updatedAt);
    }
  }

  private static final class TestSoftDeletableEntity extends SoftDeletableEntity {

    void assignDeletedAt(Instant deletedAt) {
      setDeletedAt(deletedAt);
    }
  }
}
