package com.viglet.turing.spring.jpa;

import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.ValueGenerationType;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TurUuid.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurUuidTest {

    @TurUuid
    private String fieldWithDefaultStyle;

    @TurUuid(style = org.hibernate.annotations.UuidGenerator.Style.RANDOM)
    private String fieldWithRandomStyle;

    @TurUuid(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private String fieldWithTimeStyle;

    @TurUuid(style = org.hibernate.annotations.UuidGenerator.Style.AUTO)
    private String fieldWithAutoStyle;

    @Test
    void annotationShouldHaveRuntimeRetention() {
        Retention retention = TurUuid.class.getAnnotation(Retention.class);
        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
    }

    @Test
    void annotationShouldTargetFieldAndMethod() {
        Target target = TurUuid.class.getAnnotation(Target.class);
        assertNotNull(target);
        ElementType[] targets = target.value();
        assertEquals(2, targets.length);
        assertArrayEquals(new ElementType[]{ElementType.FIELD, ElementType.METHOD}, targets);
    }

    @Test
    void annotationShouldHaveIdGeneratorType() {
        IdGeneratorType idGen = TurUuid.class.getAnnotation(IdGeneratorType.class);
        assertNotNull(idGen);
        assertEquals(TurUuidGenerator.class, idGen.value());
    }

    @Test
    void annotationShouldHaveValueGenerationType() {
        ValueGenerationType valueGen = TurUuid.class.getAnnotation(ValueGenerationType.class);
        assertNotNull(valueGen);
        assertEquals(TurUuidGenerator.class, valueGen.generatedBy());
    }

    @Test
    void defaultStyleShouldBeAuto() throws NoSuchFieldException {
        Field field = TurUuidTest.class.getDeclaredField("fieldWithDefaultStyle");
        TurUuid annotation = field.getAnnotation(TurUuid.class);
        assertNotNull(annotation);
        assertEquals(org.hibernate.annotations.UuidGenerator.Style.AUTO, annotation.style());
    }

    @Test
    void randomStyleShouldBePreserved() throws NoSuchFieldException {
        Field field = TurUuidTest.class.getDeclaredField("fieldWithRandomStyle");
        TurUuid annotation = field.getAnnotation(TurUuid.class);
        assertNotNull(annotation);
        assertEquals(org.hibernate.annotations.UuidGenerator.Style.RANDOM, annotation.style());
    }

    @Test
    void timeStyleShouldBePreserved() throws NoSuchFieldException {
        Field field = TurUuidTest.class.getDeclaredField("fieldWithTimeStyle");
        TurUuid annotation = field.getAnnotation(TurUuid.class);
        assertNotNull(annotation);
        assertEquals(org.hibernate.annotations.UuidGenerator.Style.TIME, annotation.style());
    }

    @Test
    void autoStyleShouldBePreserved() throws NoSuchFieldException {
        Field field = TurUuidTest.class.getDeclaredField("fieldWithAutoStyle");
        TurUuid annotation = field.getAnnotation(TurUuid.class);
        assertNotNull(annotation);
        assertEquals(org.hibernate.annotations.UuidGenerator.Style.AUTO, annotation.style());
    }

    @Test
    void styleEnumShouldHaveThreeValues() {
        TurUuid.Style[] values = TurUuid.Style.values();
        assertEquals(3, values.length);
    }

    @Test
    void styleEnumValueOfShouldWork() {
        assertEquals(TurUuid.Style.AUTO, TurUuid.Style.valueOf("AUTO"));
        assertEquals(TurUuid.Style.RANDOM, TurUuid.Style.valueOf("RANDOM"));
        assertEquals(TurUuid.Style.TIME, TurUuid.Style.valueOf("TIME"));
    }

    @Test
    void styleEnumValueOfInvalidShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> TurUuid.Style.valueOf("INVALID"));
    }

    @Test
    void annotationShouldBeAnnotationType() {
        assertTrue(TurUuid.class.isAnnotation());
    }
}
