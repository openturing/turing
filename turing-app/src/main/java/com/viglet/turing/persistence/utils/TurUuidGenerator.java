/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.persistence.utils;

import java.lang.reflect.Member;
import java.util.EnumSet;
import java.util.UUID;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.AnnotationBasedGenerator;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.GeneratorCreationContext;

/**
 * Custom UUID generator that allows pre-assigned identifiers.
 * * Updated to Hibernate 6 compatibility using AnnotationBasedGenerator.
 *
 * @author Alexandre Oliveira
 * @since 0.3.9
 */
public class TurUuidGenerator
        implements BeforeExecutionGenerator, AnnotationBasedGenerator<TurAssignableUuidGenerator> {

    public TurUuidGenerator() {
        // Required by Hibernate for reflection-based instantiation of generator
        // implementations
    }

    @Override
    public void initialize(TurAssignableUuidGenerator annotation, Member member, GeneratorCreationContext context) {
        // Método chamado pelo Hibernate para configurar o gerador via anotação
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner,
            Object currentValue, EventType eventType) {

        // Se o objeto já possui um ID (ex: importação de dados), mantém o atual
        if (currentValue != null) {
            return currentValue;
        }

        // Caso contrário, gera um novo UUID aleatório
        return UUID.randomUUID().toString();
    }

    @Override
    public boolean generatedOnExecution() {
        return false;
    }

    @Override
    public boolean allowAssignedIdentifiers() {
        // Mantém a funcionalidade de aceitar IDs pré-definidos
        return true;
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EnumSet.of(EventType.INSERT);
    }
}