/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.viglet.turing.api.sn.graphql;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

import graphql.scalars.ExtendedScalars;
import graphql.schema.idl.RuntimeWiring;
import lombok.extern.slf4j.Slf4j;

/**
 * GraphQL Configuration for Turing Semantic Navigation.
 * 
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Slf4j
@Configuration
public class GraphQLConfig implements RuntimeWiringConfigurer {

    private static final Pattern GRAPHQL_FIELD_NAME_PATTERN = Pattern.compile("^[_A-Za-z]\\w*$");
    private static final Set<String> STATIC_FIELDS = Set.of(
            "id", "title", "text", "url", "date", "description", "image");

    private final TurSNSiteRepositoryPort turSNSiteRepositoryPort;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public GraphQLConfig(TurSNSiteRepositoryPort turSNSiteRepositoryPort,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNSiteRepositoryPort = turSNSiteRepositoryPort;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    @Override
    public void configure(RuntimeWiring.Builder builder) {
        builder.scalar(ExtendedScalars.Json);
    }

    @Bean
    public GraphQlSourceBuilderCustomizer dynamicSearchDocumentFieldsCustomizer() {
        return builder -> {
            String dynamicSchema = buildDynamicGraphQLSchema();
            if (StringUtils.isBlank(dynamicSchema)) {
                return;
            }
            builder.schemaResources(
                    new ByteArrayResource(dynamicSchema.getBytes(StandardCharsets.UTF_8)));
        };
    }

    private String buildDynamicGraphQLSchema() {
        StringBuilder schemaBuilder = new StringBuilder();
        appendDynamicSiteNamesEnum(schemaBuilder);
        appendDynamicSearchDocumentFields(schemaBuilder);
        return schemaBuilder.toString();
    }

    private void appendDynamicSiteNamesEnum(StringBuilder schemaBuilder) {
        List<String> siteNames = turSNSiteRepositoryPort.findAllNamesOrderedByNameIgnoreCase();
        Map<String, String> enumToSiteName = TurSNSiteGraphQLNameUtils
                .buildEnumToSiteNameMap(siteNames);

        if (enumToSiteName.isEmpty()) {
            return;
        }

        schemaBuilder.append("extend enum TurSNSiteName {\n");
        enumToSiteName.keySet().forEach(enumValue -> schemaBuilder
                .append("  ")
                .append(enumValue)
                .append("\n"));
        schemaBuilder.append("}\n");

        log.info("Loaded {} dynamic GraphQL site names.", enumToSiteName.size());
    }

    private void appendDynamicSearchDocumentFields(StringBuilder schemaBuilder) {
        List<TurSNSiteFieldExt> siteFields = turSNSiteFieldExtRepository.findAll();
        if (siteFields == null || siteFields.isEmpty()) {
            return;
        }

        LinkedHashSet<String> dynamicFieldNames = siteFields.stream()
                .map(TurSNSiteFieldExt::getName)
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .map(TurSNSiteGraphQLNameUtils::toGraphQLFieldName)
                .filter(this::isValidGraphQLFieldName)
                .filter(fieldName -> !STATIC_FIELDS.contains(fieldName))
                .sorted(Comparator.comparing(String::toLowerCase))
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        if (dynamicFieldNames.isEmpty()) {
            return;
        }

        schemaBuilder.append("extend type SearchDocumentFields {\n");
        dynamicFieldNames.forEach(fieldName -> schemaBuilder
                .append("  ")
                .append(fieldName)
                .append(": JSON\n"));
        schemaBuilder.append("}\n");

        log.info("Loaded {} dynamic GraphQL search fields from site configuration.",
                dynamicFieldNames.size());
    }

    private boolean isValidGraphQLFieldName(String fieldName) {
        if (fieldName.startsWith("__")) {
            return false;
        }
        return GRAPHQL_FIELD_NAME_PATTERN.matcher(fieldName).matches();
    }
}