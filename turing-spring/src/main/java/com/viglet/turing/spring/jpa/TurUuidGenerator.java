package com.viglet.turing.spring.jpa;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.hibernate.id.uuid.UuidGenerator;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Member;

public class TurUuidGenerator extends UuidGenerator {
    public TurUuidGenerator(TurUuid config, Member idMember, CustomIdGeneratorCreationContext creationContext) {
        super(getUuidGeneratorAnnotation(config.style()), idMember, creationContext);
    }

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
                                 EventType eventType) {
        Serializable id = (Serializable) session.getEntityPersister(owner.getClass().getName(), owner)
                .getIdentifier(owner, session);
        return id != null ? id : (Serializable) super.generate(session, owner, currentValue, eventType);
    }

    private static org.hibernate.annotations.UuidGenerator getUuidGeneratorAnnotation(org.hibernate.annotations.UuidGenerator.Style style) {
        return new org.hibernate.annotations.UuidGenerator() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return org.hibernate.annotations.UuidGenerator.class;
            }

            @Override
            public org.hibernate.annotations.UuidGenerator.Style style() {
                return style;
            }
        };
    }
}
