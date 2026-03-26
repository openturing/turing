package com.viglet.turing.spring.jpa;

import org.hibernate.annotations.UuidGenerator.Style;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.id.uuid.UuidValueGenerator;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for TurUuidGenerator.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurUuidGeneratorTest {

    @Mock
    private SharedSessionContractImplementor session;

    @Mock
    private EntityPersister entityPersister;

    // --- getUuidGeneratorAnnotation (via reflection) ---

    @Test
    void getUuidGeneratorAnnotationShouldReturnAnnotationWithAutoStyle() throws Exception {
        org.hibernate.annotations.UuidGenerator annotation = invokeGetUuidGeneratorAnnotation(Style.AUTO);
        assertNotNull(annotation);
        assertEquals(Style.AUTO, annotation.style());
    }

    @Test
    void getUuidGeneratorAnnotationShouldReturnAnnotationWithRandomStyle() throws Exception {
        org.hibernate.annotations.UuidGenerator annotation = invokeGetUuidGeneratorAnnotation(Style.RANDOM);
        assertNotNull(annotation);
        assertEquals(Style.RANDOM, annotation.style());
    }

    @Test
    void getUuidGeneratorAnnotationShouldReturnAnnotationWithTimeStyle() throws Exception {
        org.hibernate.annotations.UuidGenerator annotation = invokeGetUuidGeneratorAnnotation(Style.TIME);
        assertNotNull(annotation);
        assertEquals(Style.TIME, annotation.style());
    }

    @Test
    void getUuidGeneratorAnnotationShouldReturnCorrectAnnotationType() throws Exception {
        org.hibernate.annotations.UuidGenerator annotation = invokeGetUuidGeneratorAnnotation(Style.AUTO);
        assertEquals(org.hibernate.annotations.UuidGenerator.class, annotation.annotationType());
    }

    @Test
    void getUuidGeneratorAnnotationShouldReturnUuidValueGeneratorAlgorithm() throws Exception {
        org.hibernate.annotations.UuidGenerator annotation = invokeGetUuidGeneratorAnnotation(Style.RANDOM);
        assertEquals(UuidValueGenerator.class, annotation.algorithm());
    }

    @Test
    void getUuidGeneratorAnnotationShouldReturnConsistentResults() throws Exception {
        org.hibernate.annotations.UuidGenerator annotation1 = invokeGetUuidGeneratorAnnotation(Style.RANDOM);
        org.hibernate.annotations.UuidGenerator annotation2 = invokeGetUuidGeneratorAnnotation(Style.RANDOM);
        assertEquals(annotation1.style(), annotation2.style());
        assertEquals(annotation1.annotationType(), annotation2.annotationType());
        assertEquals(annotation1.algorithm(), annotation2.algorithm());
    }

    // --- Constructor with TurUuid config ---

    @Test
    void constructorShouldAcceptAutoStyle() {
        assertDoesNotThrow(() -> createGenerator(Style.AUTO));
    }

    @Test
    void constructorShouldAcceptRandomStyle() {
        assertDoesNotThrow(() -> createGenerator(Style.RANDOM));
    }

    @Test
    void constructorShouldAcceptTimeStyle() {
        assertDoesNotThrow(() -> createGenerator(Style.TIME));
    }

    // --- generate: existing id preservation ---

    @Test
    void generateShouldReturnExistingIdWhenNotNull() throws Exception {
        String existingId = UUID.randomUUID().toString();
        Object owner = new TestEntity();

        when(session.getEntityPersister(any(String.class), eq(owner))).thenReturn(entityPersister);
        when(entityPersister.getIdentifier(owner, session)).thenReturn(existingId);

        TurUuidGenerator generator = createGenerator(Style.RANDOM);
        Serializable result = generator.generate(session, owner, null, EventType.INSERT);

        assertEquals(existingId, result);
    }

    @Test
    void generateShouldPreserveExistingUuidId() throws Exception {
        UUID existingUuid = UUID.randomUUID();
        Object owner = new TestEntity();

        when(session.getEntityPersister(any(String.class), eq(owner))).thenReturn(entityPersister);
        when(entityPersister.getIdentifier(owner, session)).thenReturn(existingUuid);

        TurUuidGenerator generator = createGenerator(Style.AUTO);
        Serializable result = generator.generate(session, owner, null, EventType.INSERT);

        assertSame(existingUuid, result);
    }

    @Test
    void generateShouldPreserveNumericId() throws Exception {
        Long numericId = 42L;
        Object owner = new TestEntity();

        when(session.getEntityPersister(any(String.class), eq(owner))).thenReturn(entityPersister);
        when(entityPersister.getIdentifier(owner, session)).thenReturn(numericId);

        TurUuidGenerator generator = createGenerator(Style.RANDOM);
        Serializable result = generator.generate(session, owner, null, EventType.INSERT);

        assertSame(numericId, result);
    }

    // --- generate: new id generation ---

    @Test
    void generateShouldDelegateToSuperWhenIdIsNull() throws Exception {
        Object owner = new TestEntity();

        when(session.getEntityPersister(any(String.class), eq(owner))).thenReturn(entityPersister);
        when(entityPersister.getIdentifier(owner, session)).thenReturn(null);

        TurUuidGenerator generator = createGenerator(Style.RANDOM);
        Serializable result = generator.generate(session, owner, null, EventType.INSERT);

        assertNotNull(result);
        assertInstanceOf(UUID.class, result);
    }

    @Test
    void generateShouldProduceUniqueIdsWhenNoExistingId() throws Exception {
        Object owner = new TestEntity();

        when(session.getEntityPersister(any(String.class), eq(owner))).thenReturn(entityPersister);
        when(entityPersister.getIdentifier(owner, session)).thenReturn(null);

        TurUuidGenerator generator = createGenerator(Style.RANDOM);

        Serializable id1 = generator.generate(session, owner, null, EventType.INSERT);
        Serializable id2 = generator.generate(session, owner, null, EventType.INSERT);

        assertNotEquals(id1, id2);
    }

    @Test
    void generateWithNonNullCurrentValueAndNullIdShouldGenerateNew() throws Exception {
        Object owner = new TestEntity();
        Object currentValue = "someCurrentValue";

        when(session.getEntityPersister(any(String.class), eq(owner))).thenReturn(entityPersister);
        when(entityPersister.getIdentifier(owner, session)).thenReturn(null);

        TurUuidGenerator generator = createGenerator(Style.RANDOM);
        Serializable result = generator.generate(session, owner, currentValue, EventType.INSERT);

        assertNotNull(result);
        assertInstanceOf(UUID.class, result);
    }

    // --- Helper methods ---

    private TurUuidGenerator createGenerator(Style style) throws Exception {
        java.lang.reflect.Field idField = TestEntity.class.getDeclaredField("id");
        TurUuid config = createTurUuidConfig(style);
        return new TurUuidGenerator(config, idField, null);
    }

    private TurUuid createTurUuidConfig(org.hibernate.annotations.UuidGenerator.Style style) {
        return new TurUuid() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return TurUuid.class;
            }

            @Override
            public org.hibernate.annotations.UuidGenerator.Style style() {
                return style;
            }
        };
    }

    private org.hibernate.annotations.UuidGenerator invokeGetUuidGeneratorAnnotation(Style style)
            throws Exception {
        Method method = TurUuidGenerator.class.getDeclaredMethod(
                "getUuidGeneratorAnnotation", Style.class);
        method.setAccessible(true);
        return (org.hibernate.annotations.UuidGenerator) method.invoke(null, style);
    }

    @SuppressWarnings("unused")
    private static class TestEntity {
        private UUID id;
    }
}
