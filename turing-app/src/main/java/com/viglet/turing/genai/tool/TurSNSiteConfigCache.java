/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import java.util.function.Supplier;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * T487 / §XXVIII.2 — dedicated read-model cache for the SN site + field
 * configuration on the Semantic Navigation hot path.
 *
 * <p>The {@code dsl_get_mappings} tool resolves an SN site by name and reads its
 * enabled field set on every invocation the LLM makes during a chat session —
 * each call hitting {@code findByNameIgnoreCase} + {@code findByTurSNSiteAndEnabled}.
 * The field configuration is deterministic per site until an operator edits it,
 * so this bean memoizes the immutable {@link TurSNSiteFieldConfigModel}
 * projection by the (lowercased) site name, skipping both queries on a hit.
 *
 * <p><b>Why this is Block AC-safe.</b> The cached value is a flat read-model
 * with no JPA entities or lazy proxies and no session affinity — never a
 * {@code TurSNSite} / {@code TurSNSiteFieldExt} entity. So a hit is safe to read
 * anywhere, outside a transaction; the property the old repository-level entity
 * cache (T486) never had. The model is {@link java.io.Serializable} so it
 * round-trips through a clustered Hazelcast {@code IMap}.
 *
 * <p><b>Why a separate bean.</b> Spring's {@code @Cacheable} is proxy-based, so
 * the {@code TurDslToolService} cannot memoize via a {@code this} call (it would
 * no-op). Routing the cacheable call through this injected bean keeps the Spring
 * proxy in the path — the canonical pattern from {@code TurChatFlowGraphCache}
 * and {@code TurRagRerankCache}.
 *
 * <p><b>Eviction.</b> The {@code turSNSiteFieldConfig} cache name is cleared by
 * {@link com.viglet.turing.sn.snapshot.TurSNSiteSnapshotEvictionListener} — the
 * JPA callback already attached to {@code TurSNSite}, {@code TurSNSiteFieldExt}
 * and the rest of the SN entity graph. Any field/site write therefore drops the
 * memoized config (coarse, whole-cache clear) so the next tool call rebuilds it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurSNSiteConfigCache {

    /** Cache holding the {@link TurSNSiteFieldConfigModel} per lowercased site name. */
    public static final String FIELD_CONFIG_CACHE = "turSNSiteFieldConfig";

    /**
     * Returns the cached field configuration for {@code siteNameKey}, computing
     * it via {@code load} on a miss. The {@code load} supplier is intentionally
     * excluded from the cache key (only {@code siteNameKey} forms it); on a hit
     * the supplier — which issues the site + field queries — is never invoked.
     *
     * <p>{@code unless = "#result == null"} keeps an unknown site (load returned
     * {@code null}) out of the cache so a freshly-created site is picked up on
     * the next call rather than being negatively pinned.
     *
     * @param siteNameKey the lowercased site name — the only part of the key
     * @param load        produces the field-config model (or {@code null} when
     *                    the site does not exist) on a cache miss
     * @return the field-config model, or {@code null} when the site is unknown
     */
    @Cacheable(cacheNames = FIELD_CONFIG_CACHE, key = "#siteNameKey",
            condition = "#siteNameKey != null", unless = "#result == null")
    public TurSNSiteFieldConfigModel fieldConfig(String siteNameKey,
            Supplier<TurSNSiteFieldConfigModel> load) {
        return load.get();
    }
}
