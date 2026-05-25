/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatflow;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.dto.agent.TurChatFlowImportDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowRecipeDto;
import com.viglet.turing.persistence.dto.agent.TurChatFlowRecipeSummaryDto;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * T96 / §VII.11.f — loads the bundled Turing Recipes from the classpath at
 * startup and exposes them through a tiny read API used by the admin
 * marketplace UI.
 *
 * <p>Recipes are JSON files under
 * {@code turing-app/src/main/resources/flow-recipes/*.json} whose shape
 * mirrors {@link TurChatFlowRecipeDto} 1-to-1. Each entry's
 * {@code bundle} reuses the existing import-bundle payload shape so
 * authoring a new recipe is "write a JSON" — no Java code change needed.
 *
 * <p>Recipes never change at runtime, so we parse them once at boot and
 * cache by id in an insertion-ordered map. Failures during parsing are
 * logged and skipped — one broken recipe must not stall the whole app
 * (the test suite has separate coverage to catch them in CI).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatFlowRecipeService {

    /**
     * Classpath glob the loader scans on boot. Matches both the
     * Maven-packaged jar layout (recipes copied to {@code target/classes/flow-recipes/})
     * and a running dev IDE pointing at {@code src/main/resources/flow-recipes/}.
     */
    static final String RECIPE_CLASSPATH_PATTERN = "classpath:/flow-recipes/*.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Insertion-ordered so summary listing returns recipes in the same order on every call. */
    private final Map<String, TurChatFlowRecipeDto> recipesById = new LinkedHashMap<>();

    @PostConstruct
    void loadFromClasspath() {
        loadFrom(new PathMatchingResourcePatternResolver());
    }

    /** Package-private hook so the test can substitute the resource resolver. */
    void loadFrom(PathMatchingResourcePatternResolver resolver) {
        recipesById.clear();
        Resource[] resources;
        try {
            resources = resolver.getResources(RECIPE_CLASSPATH_PATTERN);
        } catch (IOException e) {
            log.warn("[Recipes] No recipes loaded — classpath scan failed: {}", e.getMessage());
            return;
        }
        List<TurChatFlowRecipeDto> ordered = new ArrayList<>(resources.length);
        for (Resource resource : resources) {
            try (InputStream stream = resource.getInputStream()) {
                TurChatFlowRecipeDto recipe = OBJECT_MAPPER.readValue(stream,
                        TurChatFlowRecipeDto.class);
                if (recipe.getId() == null || recipe.getId().isBlank()) {
                    log.warn("[Recipes] Skipping recipe at {} — missing id field",
                            resource.getDescription());
                    continue;
                }
                ordered.add(recipe);
            } catch (IOException | JacksonException e) {
                log.warn("[Recipes] Failed to parse recipe at {}: {}",
                        resource.getDescription(), e.getMessage());
            }
        }
        // Sort by vertical then by name so the catalog feels organized in
        // the UI even if filesystem iteration order is non-deterministic.
        ordered.sort(Comparator
                .comparing((TurChatFlowRecipeDto r) -> r.getVertical() == null ? "" : r.getVertical())
                .thenComparing(r -> r.getName() == null ? "" : r.getName()));
        for (TurChatFlowRecipeDto recipe : ordered) {
            recipesById.put(recipe.getId(), recipe);
        }
        log.info("[Recipes] Loaded {} recipe(s) from {}: {}",
                recipesById.size(), RECIPE_CLASSPATH_PATTERN, recipesById.keySet());
    }

    /**
     * Cheap catalog listing — metadata + bundle counts, no full graph
     * payload. Each call iterates the cache so admin edits to the JSON
     * files (after a restart) reflect on the next boot.
     */
    public List<TurChatFlowRecipeSummaryDto> list() {
        List<TurChatFlowRecipeSummaryDto> out = new ArrayList<>(recipesById.size());
        for (TurChatFlowRecipeDto recipe : recipesById.values()) {
            out.add(summarize(recipe));
        }
        return out;
    }

    /** Full recipe — used by the preview pane and by the install controller. */
    public Optional<TurChatFlowRecipeDto> get(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(recipesById.get(id));
    }

    private static TurChatFlowRecipeSummaryDto summarize(TurChatFlowRecipeDto recipe) {
        List<TurChatFlowImportDto> bundle = recipe.getBundle() == null
                ? List.of()
                : recipe.getBundle();
        int flowCount = bundle.size();
        int personaCount = 0;
        int slotCount = 0;
        for (TurChatFlowImportDto item : bundle) {
            if (item.getPersonas() != null) personaCount += item.getPersonas().size();
            if (item.getSlots() != null) slotCount += item.getSlots().size();
        }
        return new TurChatFlowRecipeSummaryDto(
                recipe.getId(),
                recipe.getVersion(),
                recipe.getName(),
                recipe.getDescription(),
                recipe.getVertical(),
                recipe.getTags() == null ? List.of() : recipe.getTags(),
                flowCount, personaCount, slotCount);
    }
}
