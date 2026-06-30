/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.media;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.viglet.turing.api.sn.queue.TurSNProcessQueue;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * T501 / §X.19 — boots the full Spring context to verify the index-time media
 * enricher is registered with its real {@code TurGeminiVideoUnderstandingService}
 * dependency, and that it was wired as a new constructor parameter of
 * {@link TurSNProcessQueue} (the actual regression risk of this change). Then
 * exercises the no-op paths that need no live Gemini key — a site with no GenAI,
 * and the disabled flag — confirming the disabled path is inert and never throws.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurSNGeminiMediaIndexerIT extends AbstractTuringSpringIT {

    @Autowired
    private TurSNGeminiMediaIndexer indexer;

    // Proves the new constructor dependency resolves in the full context.
    @Autowired
    private TurSNProcessQueue processQueue;

    @Test
    void beansAreWiredIntoContext() {
        assertThat(indexer).isNotNull();
        assertThat(processQueue).isNotNull();
    }

    @Test
    void noGenAiSiteIsInertNoOp() {
        TurSNSite site = new TurSNSite();
        site.setName("plain-site");

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://cdn.example.com/clip.mp4");

        indexer.enrich(site, attributes);

        // No GenAI → no understanding, document untouched.
        assertThat(attributes).doesNotContainKey(TurSNFieldName.TEXT);
        assertThat(attributes.get(TurSNFieldName.URL)).isEqualTo("https://cdn.example.com/clip.mp4");
    }

    @Test
    void disabledFlagIsInertNoOp() {
        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setMediaUnderstandingIndexingEnabled(false);

        TurSNSite site = new TurSNSite();
        site.setName("disabled-site");
        site.setTurSNSiteGenAi(genAi);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put(TurSNFieldName.URL, "https://youtu.be/abc123");

        indexer.enrich(site, attributes);

        assertThat(attributes).doesNotContainKey(TurSNFieldName.TEXT);
    }
}
