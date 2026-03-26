package com.viglet.turing.persistence.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

import org.hibernate.annotations.IdGeneratorType;
import org.junit.jupiter.api.Test;

/**
 * Tests for TurAssignableUuidGenerator.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurAssignableUuidGeneratorTest {

    @Test
    void shouldHaveRuntimeRetention() {
        Retention retention = TurAssignableUuidGenerator.class.getAnnotation(Retention.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
    }

    @Test
    void shouldTargetFieldAndMethod() {
        Target target = TurAssignableUuidGenerator.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(Arrays.asList(target.value())).contains(ElementType.FIELD);
        assertThat(Arrays.asList(target.value())).contains(ElementType.METHOD);
    }

    @Test
    void shouldHaveExactlyTwoTargets() {
        Target target = TurAssignableUuidGenerator.class.getAnnotation(Target.class);

        assertThat(target).isNotNull();
        assertThat(target.value()).hasSize(2);
    }

    @Test
    void shouldUseTurUuidGeneratorAsIdGeneratorType() {
        IdGeneratorType idGeneratorType = TurAssignableUuidGenerator.class.getAnnotation(IdGeneratorType.class);

        assertThat(idGeneratorType).isNotNull();
        assertThat(idGeneratorType.value()).isEqualTo(TurUuidGenerator.class);
    }

    @Test
    void shouldBeAnnotationType() {
        assertThat(TurAssignableUuidGenerator.class.isAnnotation()).isTrue();
    }

    @Test
    void shouldHaveThreeAnnotations() {
        // @IdGeneratorType, @Retention, @Target
        assertThat(TurAssignableUuidGenerator.class.getDeclaredAnnotations()).hasSize(3);
    }
}
