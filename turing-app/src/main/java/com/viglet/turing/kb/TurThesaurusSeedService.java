/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.kb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.dto.kb.TurMicrothesaurusDto;
import com.viglet.turing.persistence.dto.kb.TurThesaurusSeedDto;
import com.viglet.turing.persistence.model.kb.TurKnowledgeBase;

/**
 * T674 / §XL (Block AQ) — the importable <em>seed library</em>: pre-populated,
 * AI-generated microthesaurus hierarchies (education domain first, pt/en/it)
 * shipped as classpath authority-file bundles under {@code kb/seed/<domain>/}.
 * Following the T373/T655 seed-bundle precedent they are <strong>not</strong>
 * loaded at startup — a user browses the library and imports one into a Knowledge
 * Base on demand, reusing the T673 {@link TurAuthorityFileImportService}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurThesaurusSeedService {

    private static final String SEED_PATTERN = "classpath*:kb/seed/**/*.authority-file.xml";
    private static final String SEED_SUFFIX = ".authority-file.xml";

    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
    private final TurAuthorityFileImportService importService;

    public TurThesaurusSeedService(TurAuthorityFileImportService importService) {
        this.importService = importService;
    }

    /** Lists every bundled seed microthesaurus available to import. */
    public List<TurThesaurusSeedDto> list() {
        List<TurThesaurusSeedDto> seeds = new ArrayList<>();
        for (Resource resource : resolveSeedResources()) {
            toMetadata(resource).ifPresent(seeds::add);
        }
        seeds.sort(Comparator.comparing(TurThesaurusSeedDto::domain)
                .thenComparing(TurThesaurusSeedDto::language));
        return seeds;
    }

    /** Imports the seed identified by {@code seedId} into {@code kb}. */
    @Transactional
    public TurMicrothesaurusDto importSeed(TurKnowledgeBase kb, String seedId) {
        Resource resource = resolveSeedResources().stream()
                .filter(r -> seedId(r).equals(seedId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown seed: " + seedId));
        try {
            byte[] xml = resource.getInputStream().readAllBytes();
            return importService.importAuthorityFile(kb, xml, domainOf(resource));
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read seed '" + seedId + "': "
                    + e.getMessage(), e);
        }
    }

    private List<Resource> resolveSeedResources() {
        try {
            return List.of(resourceResolver.getResources(SEED_PATTERN));
        } catch (IOException e) {
            return List.of();
        }
    }

    private Optional<TurThesaurusSeedDto> toMetadata(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null || !filename.endsWith(SEED_SUFFIX)) {
            return Optional.empty();
        }
        String id = seedId(resource);
        return Optional.of(new TurThesaurusSeedDto(id, readName(resource, id),
                domainOf(resource), languageOf(id)));
    }

    private String seedId(Resource resource) {
        String filename = StringUtils.defaultString(resource.getFilename());
        return filename.endsWith(SEED_SUFFIX)
                ? filename.substring(0, filename.length() - SEED_SUFFIX.length())
                : filename;
    }

    /** The domain is the folder under {@code kb/seed/} (e.g. {@code education} → {@code EDUCATION}). */
    private String domainOf(Resource resource) {
        String path = resourcePath(resource);
        int seedIdx = path.indexOf("/seed/");
        if (seedIdx < 0) {
            return "GENERAL";
        }
        String rest = path.substring(seedIdx + "/seed/".length());
        int slash = rest.indexOf('/');
        return slash > 0 ? rest.substring(0, slash).toUpperCase(Locale.ROOT) : "GENERAL";
    }

    /** Language is the trailing {@code -xx} of the seed id (e.g. {@code educacao-pt} → {@code pt}). */
    private String languageOf(String seedId) {
        int dash = seedId.lastIndexOf('-');
        return dash > 0 && dash < seedId.length() - 1 ? seedId.substring(dash + 1) : "";
    }

    private String resourcePath(Resource resource) {
        try {
            return resource.getURL().getPath();
        } catch (IOException e) {
            return StringUtils.defaultString(resource.getFilename());
        }
    }

    /** Light read of {@code authorityFile/name} for the display label; falls back to the id. */
    private String readName(Resource resource, String fallback) {
        try {
            String xml = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int nameStart = xml.indexOf("<name>");
            int nameEnd = xml.indexOf("</name>");
            if (nameStart >= 0 && nameEnd > nameStart) {
                String name = xml.substring(nameStart + "<name>".length(), nameEnd).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        } catch (IOException ignored) {
            // fall through to the id
        }
        return fallback;
    }
}
