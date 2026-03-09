package com.viglet.turing.persistence.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import org.hibernate.annotations.IdGeneratorType;
import org.junit.jupiter.api.Test;

class TurAssignableUuidGeneratorTest {

    @Test
    void shouldHaveRuntimeRetentionAndFieldMethodTargets() {
        Retention retention = TurAssignableUuidGenerator.class.getAnnotation(Retention.class);
        Target target = TurAssignableUuidGenerator.class.getAnnotation(Target.class);

        assertNotNull(retention);
        assertEquals(RetentionPolicy.RUNTIME, retention.value());

        assertNotNull(target);
        assertTrue(Arrays.asList(target.value()).contains(ElementType.FIELD));
        assertTrue(Arrays.asList(target.value()).contains(ElementType.METHOD));
    }

    @Test
    void shouldUseTurUuidGeneratorAsIdGeneratorType() {
        IdGeneratorType idGeneratorType = TurAssignableUuidGenerator.class.getAnnotation(IdGeneratorType.class);

        assertNotNull(idGeneratorType);
        assertEquals(TurUuidGenerator.class, idGeneratorType.value());
    }
}
