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

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.sn.TurSNSite;

/**
 * F.7 / §X.8.b — single source of truth for the per-site AI Insights prompt.
 *
 * <p>Both the on-demand insights endpoint ({@code TurSNSiteSummaryAPI}) and the
 * nightly Batch summarization job ({@code TurNightlyBatchSummarizationJob}) build
 * the <em>identical</em> {@code (cacheKey, systemPrompt, userData)} triple from a
 * site, so a batch-warmed entry is a byte-for-byte cache hit for the synchronous
 * path. The cache key is the site id — the same key
 * {@code TurLlmSummaryService.generate} uses.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Component
public class TurSNSiteInsightsPromptBuilder {

    static final String ANALYSIS_INSTRUCTIONS = """
            Analyze the data provided and generate a comprehensive summary in Markdown format. \
            Include these sections:
            ## Overview
            Brief summary of the site configuration and health.
            ## Search Activity
            Analysis of search metrics and top terms.
            ## Configuration Review
            Review of fields, locales, facets, and behavior settings.
            ## Suggestions
            Actionable recommendations to improve search quality and user experience.

            Be concise but insightful. Use bullet points where appropriate. \
            Highlight any potential issues or misconfigurations.""";

    private final TurSNSiteDataCollectorService siteDataCollectorService;

    public TurSNSiteInsightsPromptBuilder(TurSNSiteDataCollectorService siteDataCollectorService) {
        this.siteDataCollectorService = siteDataCollectorService;
    }

    /** The cache key + system/user prompts for a site's AI Insights summary. */
    public record InsightsPrompt(String cacheKey, String systemPrompt, String userData) {
    }

    public InsightsPrompt build(TurSNSite site) {
        String dataSummary = siteDataCollectorService.collectSiteData(site);

        String sitePrompt = site.getTurSNSiteGenAi() != null
                ? site.getTurSNSiteGenAi().getSitePrompt()
                : null;

        String systemPrompt = StringUtils.hasText(sitePrompt)
                ? sitePrompt + "\n\n" + ANALYSIS_INSTRUCTIONS
                : "You are an enterprise search expert analyzing a Semantic Navigation site "
                  + "from the Turing platform. " + ANALYSIS_INSTRUCTIONS;

        String userData = dataSummary
                + "\nPlease analyze all this data and provide a comprehensive summary with suggestions.";

        return new InsightsPrompt(site.getId(), systemPrompt, userData);
    }
}
