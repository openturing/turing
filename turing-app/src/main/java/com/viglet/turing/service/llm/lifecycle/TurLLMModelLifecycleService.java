/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.llm.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.genai.provider.llm.TurLlmModelKind;
import com.viglet.turing.genai.provider.llm.TurLlmModelMetadata;
import com.viglet.turing.genai.provider.llm.TurLlmModelOption;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T782 / §LIII.2 (Block BE) — scans configured LLM instances for models the
 * public catalog marks {@code deprecated} or {@code status = DEPRECATED/RETIRED}
 * and proposes a GA replacement (same vendor + kind, covering the deprecated
 * model's capabilities, at a similar-or-lower indicative price). Agents inherit
 * their model from the instance, so scanning instances covers agent usage too.
 *
 * <p>The catalog is a live signal, so the flag is computed on demand (never
 * persisted stale) for the admin banner + picker; a daily {@code @Scheduled} pass
 * logs a warning so the signal also reaches operators who aren't looking at the UI.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurLLMModelLifecycleService {

    private static final Set<String> DEPRECATED_STATUSES = Set.of("DEPRECATED", "RETIRED");

    private final TurLLMInstanceRepository instanceRepository;
    private final TurLlmModelCatalog modelCatalog;

    public TurLLMModelLifecycleService(TurLLMInstanceRepository instanceRepository,
            TurLlmModelCatalog modelCatalog) {
        this.instanceRepository = instanceRepository;
        this.modelCatalog = modelCatalog;
    }

    /**
     * An instance whose configured default model the catalog marks end-of-life,
     * with a suggested GA replacement when one exists.
     */
    public record DeprecatedModelUsage(
            String instanceId,
            String instanceTitle,
            String vendorId,
            String modelName,
            String status,
            boolean deprecated,
            String replacementModelId,
            String replacementLabel) {
    }

    /** All instances whose configured model is deprecated/retired per the catalog. */
    public List<DeprecatedModelUsage> findDeprecatedModelsInUse() {
        List<DeprecatedModelUsage> out = new ArrayList<>();
        for (TurLLMInstance instance : instanceRepository.findAll()) {
            usageFor(instance).ifPresent(out::add);
        }
        return out;
    }

    private Optional<DeprecatedModelUsage> usageFor(TurLLMInstance instance) {
        String modelName = instance.getModelName();
        if (!StringUtils.hasText(modelName)) {
            return Optional.empty();
        }
        List<TurLlmModelOption> vendorModels = vendorModels(instance.getTurLLMVendor());
        TurLlmModelOption current = findById(vendorModels, modelName);
        if (current == null || !isEndOfLife(current.metadata())) {
            return Optional.empty();
        }
        TurLlmModelOption replacement = suggestReplacement(vendorModels, current);
        TurLlmModelMetadata meta = current.metadata();
        return Optional.of(new DeprecatedModelUsage(
                instance.getId(),
                instance.getTitle(),
                vendorSlug(instance.getTurLLMVendor()),
                modelName,
                meta == null ? null : meta.status(),
                meta != null && Boolean.TRUE.equals(meta.deprecated()),
                replacement == null ? null : replacement.id(),
                replacement == null ? null : replacement.label()));
    }

    /** Catalog models for an instance's vendor (empty when the vendor is unknown). */
    private List<TurLlmModelOption> vendorModels(TurLLMVendor vendor) {
        String slug = vendorSlug(vendor);
        if (slug == null) {
            return List.of();
        }
        return modelCatalog.allModels().getOrDefault(slug, List.of());
    }

    private static String vendorSlug(TurLLMVendor vendor) {
        if (vendor == null) {
            return null;
        }
        String slug = StringUtils.hasText(vendor.getPlugin()) ? vendor.getPlugin() : vendor.getId();
        return StringUtils.hasText(slug) ? slug.toLowerCase(Locale.ROOT) : null;
    }

    private static TurLlmModelOption findById(List<TurLlmModelOption> models, String id) {
        return models.stream().filter(o -> id.equals(o.id())).findFirst().orElse(null);
    }

    /** True when the catalog marks the model deprecated or DEPRECATED/RETIRED. */
    public static boolean isEndOfLife(TurLlmModelMetadata meta) {
        if (meta == null) {
            return false;
        }
        if (Boolean.TRUE.equals(meta.deprecated())) {
            return true;
        }
        return meta.status() != null
                && DEPRECATED_STATUSES.contains(meta.status().trim().toUpperCase(Locale.ROOT));
    }

    /**
     * Best GA replacement for a deprecated model: same vendor + kind, not itself
     * end-of-life, covering the deprecated model's capabilities, at a similar-or-
     * lower indicative input price when both are known. Ranked by intelligence
     * index desc then input price asc. Returns {@code null} when nothing qualifies.
     */
    static TurLlmModelOption suggestReplacement(List<TurLlmModelOption> vendorModels, TurLlmModelOption current) {
        TurLlmModelKind kind = current.kind();
        TurLlmModelMetadata currentMeta = current.metadata();
        Double currentPrice = inputPrice(currentMeta);
        Set<String> requiredCaps = capabilities(currentMeta);

        return vendorModels.stream()
                .filter(o -> !o.id().equals(current.id()))
                .filter(o -> o.kind() == kind)
                .filter(o -> !isEndOfLife(o.metadata()))
                .filter(o -> coversCapabilities(o.metadata(), requiredCaps))
                .filter(o -> priceWithinBudget(o.metadata(), currentPrice))
                .min((a, b) -> {
                    int byIntelligence = Double.compare(intelligence(b.metadata()), intelligence(a.metadata()));
                    if (byIntelligence != 0) {
                        return byIntelligence;
                    }
                    return Double.compare(priceOrMax(a.metadata()), priceOrMax(b.metadata()));
                })
                .orElse(null);
    }

    private static boolean coversCapabilities(TurLlmModelMetadata meta, Set<String> required) {
        if (required.isEmpty()) {
            return true;
        }
        return capabilities(meta).containsAll(required);
    }

    private static boolean priceWithinBudget(TurLlmModelMetadata meta, Double budget) {
        if (budget == null) {
            return true;
        }
        Double price = inputPrice(meta);
        return price == null || price <= budget;
    }

    private static Set<String> capabilities(TurLlmModelMetadata meta) {
        if (meta == null || meta.capabilities() == null) {
            return Set.of();
        }
        return meta.capabilities().stream().map(c -> c.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
    }

    private static Double inputPrice(TurLlmModelMetadata meta) {
        return meta == null || meta.pricing() == null ? null : meta.pricing().inputPer1M();
    }

    private static double priceOrMax(TurLlmModelMetadata meta) {
        Double price = inputPrice(meta);
        return price == null ? Double.MAX_VALUE : price;
    }

    private static double intelligence(TurLlmModelMetadata meta) {
        if (meta == null || meta.benchmarks() == null || meta.benchmarks().intelligenceIndex() == null) {
            return -1;
        }
        return meta.benchmarks().intelligenceIndex();
    }

    /**
     * Daily operator-facing scan: logs a warning naming instances on end-of-life
     * models so the signal reaches logs/alerts, not just the admin UI. Best-effort.
     */
    @Scheduled(
            initialDelayString = "${turing.model-catalog.deprecation-scan.initial-delay-ms:30000}",
            fixedDelayString = "${turing.model-catalog.deprecation-scan.interval-ms:86400000}")
    void scanForDeprecatedModels() {
        try {
            List<DeprecatedModelUsage> usages = findDeprecatedModelsInUse();
            for (DeprecatedModelUsage usage : usages) {
                log.warn("LLM instance '{}' ({}) uses end-of-life model '{}' (status={}); suggested replacement: {}",
                        usage.instanceTitle(), usage.instanceId(), usage.modelName(),
                        usage.status(), usage.replacementModelId() == null ? "none" : usage.replacementModelId());
            }
        } catch (Exception e) {
            log.debug("Deprecated-model scan failed: {}", e.getMessage());
        }
    }
}
