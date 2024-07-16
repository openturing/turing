package com.viglet.turing.spring.jpa;

import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.ValueGenerationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@IdGeneratorType(TurUuidGenerator.class)
@ValueGenerationType(
        generatedBy = TurUuidGenerator.class
)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface TurUuid {
    org.hibernate.annotations.UuidGenerator.Style style()
            default org.hibernate.annotations.UuidGenerator.Style.AUTO;

    enum Style {
        AUTO,
        RANDOM,
        TIME;

        private Style() {
        }
    }
}
