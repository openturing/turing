/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * F.7 / §X.8.b — locks the cache-key + prompt parity the nightly Batch job
 * relies on to warm the synchronous insights path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurSNSiteInsightsPromptBuilderTest {

    @Mock
    private TurSNSiteDataCollectorService dataCollectorService;

    @InjectMocks
    private TurSNSiteInsightsPromptBuilder builder;

    @Test
    void build_cacheKeyIsSiteId_andUserDataWrapsCollectedData() {
        TurSNSite site = new TurSNSite();
        site.setId("site-42");
        when(dataCollectorService.collectSiteData(site)).thenReturn("DATA");

        TurSNSiteInsightsPromptBuilder.InsightsPrompt prompt = builder.build(site);

        assertThat(prompt.cacheKey()).isEqualTo("site-42");
        assertThat(prompt.userData()).startsWith("DATA");
        assertThat(prompt.systemPrompt())
                .contains(TurSNSiteInsightsPromptBuilder.ANALYSIS_INSTRUCTIONS);
    }

    @Test
    void build_defaultSystemPrompt_whenNoSiteGenAiPrompt() {
        TurSNSite site = new TurSNSite();
        site.setId("s1");
        when(dataCollectorService.collectSiteData(site)).thenReturn("x");

        TurSNSiteInsightsPromptBuilder.InsightsPrompt prompt = builder.build(site);

        assertThat(prompt.systemPrompt()).startsWith("You are an enterprise search expert");
    }
}
