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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.llm.TurCatalogChanges;
import com.viglet.turing.genai.provider.llm.TurCatalogChanges.TurCatalogChangeEntry;
import com.viglet.turing.genai.provider.llm.TurLlmModelCatalog;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMVendor;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;

/**
 * T788 / §LIII.4 (Block BE) — turns the catalog change feed (T788 in
 * {@link TurLlmModelCatalog#catalogChanges()}) into admin notifications about the
 * models an operator actually uses: a configured model that was <b>removed</b>
 * from the catalog (likely retired), <b>superseded</b> (removed while a newer
 * same-vendor model was added), or <b>changed</b> (e.g. repriced). Read-only over
 * the feed + configured instances — the catalog becomes a live signal, not just a
 * startup snapshot.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Service
public class TurLLMCatalogChangeService {

    /** The nature of a change to a used model. */
    public enum ChangeType {
        REMOVED, SUPERSEDED, CHANGED
    }

    private final TurLLMInstanceRepository instanceRepository;
    private final TurLlmModelCatalog modelCatalog;

    public TurLLMCatalogChangeService(TurLLMInstanceRepository instanceRepository,
            TurLlmModelCatalog modelCatalog) {
        this.instanceRepository = instanceRepository;
        this.modelCatalog = modelCatalog;
    }

    /** A change-feed notification for a model an operator has configured. */
    public record ChangeNotification(
            ChangeType type,
            String vendorId,
            String modelId,
            String label,
            String detail,
            List<String> replacementCandidates) {
    }

    /**
     * Notifications for configured models touched by the latest change feed. Empty
     * when the feed is empty or none of its entries match a used model.
     */
    public List<ChangeNotification> notificationsForUsedModels() {
        TurCatalogChanges changes = modelCatalog.catalogChanges();
        Set<String> usedKeys = usedModelKeys();
        if (usedKeys.isEmpty()) {
            return List.of();
        }
        List<ChangeNotification> out = new ArrayList<>();
        for (TurCatalogChangeEntry removed : changes.removed()) {
            if (matchesUsed(removed, usedKeys)) {
                List<String> supersededBy = sameVendorAdded(removed, changes.added());
                out.add(new ChangeNotification(
                        supersededBy.isEmpty() ? ChangeType.REMOVED : ChangeType.SUPERSEDED,
                        removed.vendor(), removed.id(), removed.label(),
                        removed.detail(), supersededBy));
            }
        }
        for (TurCatalogChangeEntry changed : changes.changed()) {
            if (matchesUsed(changed, usedKeys)) {
                out.add(new ChangeNotification(ChangeType.CHANGED,
                        changed.vendor(), changed.id(), changed.label(), changed.detail(), List.of()));
            }
        }
        return out;
    }

    /** {@code vendorSlug|modelName} keys for every configured instance's default model. */
    private Set<String> usedModelKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (TurLLMInstance instance : instanceRepository.findAll()) {
            String slug = vendorSlug(instance.getTurLLMVendor());
            if (slug != null && StringUtils.hasText(instance.getModelName())) {
                keys.add(slug + "|" + instance.getModelName());
            }
        }
        return keys;
    }

    private static boolean matchesUsed(TurCatalogChangeEntry entry, Set<String> usedKeys) {
        if (entry.vendor() == null || entry.id() == null) {
            return false;
        }
        return usedKeys.contains(entry.vendor().toLowerCase(Locale.ROOT) + "|" + entry.id());
    }

    /** Ids of same-vendor models added in the feed — likely replacements for a removed one. */
    private static List<String> sameVendorAdded(TurCatalogChangeEntry removed, List<TurCatalogChangeEntry> added) {
        if (removed.vendor() == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (TurCatalogChangeEntry entry : added) {
            if (removed.vendor().equalsIgnoreCase(entry.vendor()) && entry.id() != null) {
                ids.add(entry.id());
            }
        }
        return ids;
    }

    private static String vendorSlug(TurLLMVendor vendor) {
        if (vendor == null) {
            return null;
        }
        String slug = StringUtils.hasText(vendor.getPlugin()) ? vendor.getPlugin() : vendor.getId();
        return StringUtils.hasText(slug) ? slug.toLowerCase(Locale.ROOT) : null;
    }
}
