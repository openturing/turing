package com.viglet.turing.persistence.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.generator.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for TurUuidGenerator.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurUuidGeneratorTest {

    private TurUuidGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new TurUuidGenerator();
    }

    // --- generate ---

    @Test
    void shouldKeepAssignedIdentifierWhenCurrentValueIsNotNull() {
        Object currentValue = "existing-id";

        Object generated = generator.generate(null, new Object(), currentValue, EventType.INSERT);

        assertThat(generated).isSameAs(currentValue);
    }

    @Test
    void shouldGenerateUuidWhenCurrentValueIsNull() {
        Object generated = generator.generate(null, new Object(), null, EventType.INSERT);

        assertThat(generated).isNotNull();
        assertThat(generated).isInstanceOf(String.class);
        assertThat((String) generated).hasSize(36);
    }

    @Test
    void shouldGenerateValidUuidFormat() {
        String generated = (String) generator.generate(null, new Object(), null, EventType.INSERT);

        // Should not throw
        UUID parsed = UUID.fromString(generated);
        assertThat(parsed).isNotNull();
        assertThat(parsed).hasToString(generated);
    }

    @Test
    void shouldGenerateUniqueUuidsOnEachCall() {
        Set<String> uuids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String uuid = (String) generator.generate(null, new Object(), null, EventType.INSERT);
            uuids.add(uuid);
        }

        assertThat(uuids).hasSize(100);
    }

    @Test
    void shouldPreserveStringId() {
        String id = "my-custom-id-123";
        Object generated = generator.generate(null, new Object(), id, EventType.INSERT);

        assertThat(generated).isEqualTo("my-custom-id-123");
    }

    @Test
    void shouldPreserveUuidStringId() {
        String uuidStr = UUID.randomUUID().toString();
        Object generated = generator.generate(null, new Object(), uuidStr, EventType.INSERT);

        assertThat(generated).isEqualTo(uuidStr);
    }

    @Test
    void shouldPreserveNonStringId() {
        Integer numericId = 42;
        Object generated = generator.generate(null, new Object(), numericId, EventType.INSERT);

        assertThat(generated).isSameAs(numericId);
    }

    @Test
    void shouldHandleNullOwner() {
        Object generated = generator.generate(null, null, null, EventType.INSERT);

        assertThat(generated).isNotNull();
        assertThat(generated).isInstanceOf(String.class);
    }

    @Test
    void shouldHandleNullSession() {
        Object generated = generator.generate(null, new Object(), null, EventType.INSERT);

        assertThat(generated).isNotNull();
    }

    // --- generatedOnExecution ---

    @Test
    void shouldNotBeGeneratedOnExecution() {
        assertThat(generator.generatedOnExecution()).isFalse();
    }

    // --- allowAssignedIdentifiers ---

    @Test
    void shouldAllowAssignedIdentifiers() {
        assertThat(generator.allowAssignedIdentifiers()).isTrue();
    }

    // --- getEventTypes ---

    @Test
    void shouldOnlyHandleInsertEvents() {
        assertThat(generator.getEventTypes()).isEqualTo(EnumSet.of(EventType.INSERT));
    }

    @Test
    void shouldNotHandleUpdateEvents() {
        assertThat(generator.getEventTypes()).doesNotContain(EventType.UPDATE);
    }

    @Test
    void shouldHaveSingleEventType() {
        assertThat(generator.getEventTypes()).hasSize(1);
    }

    // --- initialize ---

    @Test
    void initializeShouldNotThrow() {
        // initialize is a no-op, but should not throw
        assertDoesNotThrow(() -> generator.initialize(null, null, null));
    }

    // --- Default constructor ---

    @Test
    void defaultConstructorShouldCreateInstance() {
        TurUuidGenerator gen = new TurUuidGenerator();
        assertThat(gen).isNotNull();
    }
}
