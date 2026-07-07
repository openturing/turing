/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.rerank;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.BedrockRerankingModelConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankDocumentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankQueryContentType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankResponse;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSource;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankSourceType;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankTextDocument;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RerankingConfigurationType;

/**
 * T521 / §XXVIII.17 — reranks via the managed AWS Bedrock {@code Rerank} API, a
 * Block N {@link TurRagRerankStrategy} for cloud-native deployments that don't
 * want to operate a self-hosted cross-encoder (T338). Authenticated by IAM (the
 * AWS default credentials chain), region from {@code GLOBAL_RAG_SN_RERANK_REGION}.
 *
 * <p>The rerank model is the {@code GLOBAL_RAG_SN_RERANK_MODEL} value — a full
 * Bedrock model ARN, or a bare model id (e.g. {@code amazon.rerank-v1:0}) which
 * is expanded to the foundation-model ARN for the configured region. Blank uses
 * {@code amazon.rerank-v1:0}.
 *
 * <p>Fail-open like the rest of Block N: any error (or a missing region) returns
 * an empty list and {@code TurRagReranker} keeps the retrieval order.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurBedrockRerankStrategy implements TurRagRerankStrategy {

    private static final String DEFAULT_MODEL_ID = "amazon.rerank-v1:0";

    private final TurGlobalSettingsService globalSettingsService;

    public TurBedrockRerankStrategy(TurGlobalSettingsService globalSettingsService) {
        this.globalSettingsService = globalSettingsService;
    }

    @Override
    public TurRagRerankStrategyType getType() {
        return TurRagRerankStrategyType.BEDROCK;
    }

    @Override
    public List<Document> rerank(TurRagRerankRequest request) {
        String region = globalSettingsService.getRagSnRerankRegion();
        if (!StringUtils.hasText(region)) {
            log.debug("[RAG] Bedrock reranker: no region configured; keeping retrieval order");
            return List.of();
        }
        String modelArn = resolveModelArn(region);
        List<Document> candidates = request.candidates();
        List<RerankSource> sources = new ArrayList<>(candidates.size());
        for (Document doc : candidates) {
            sources.add(RerankSource.builder()
                    .type(RerankSourceType.INLINE)
                    .inlineDocumentSource(RerankDocument.builder()
                            .type(RerankDocumentType.TEXT)
                            .textDocument(RerankTextDocument.builder()
                                    .text(doc.getText() == null ? "" : doc.getText())
                                    .build())
                            .build())
                    .build());
        }
        try (BedrockAgentRuntimeClient client = BedrockAgentRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            RerankResponse response = client.rerank(RerankRequest.builder()
                    .queries(RerankQuery.builder()
                            .type(RerankQueryContentType.TEXT)
                            .textQuery(RerankTextDocument.builder().text(request.query()).build())
                            .build())
                    .sources(sources)
                    .rerankingConfiguration(RerankingConfiguration.builder()
                            .type(RerankingConfigurationType.BEDROCK_RERANKING_MODEL)
                            .bedrockRerankingConfiguration(BedrockRerankingConfiguration.builder()
                                    .numberOfResults(Math.min(request.topK(), candidates.size()))
                                    .modelConfiguration(BedrockRerankingModelConfiguration.builder()
                                            .modelArn(modelArn)
                                            .build())
                                    .build())
                            .build())
                    .build());
            return TurRerankIndexMapper.map(candidates, orderFrom(response, candidates.size()), request.topK());
        }
    }

    /** Extracts the in-bounds, ordered candidate indices from the rerank response. */
    List<Integer> orderFrom(RerankResponse response, int candidateCount) {
        if (response == null || !response.hasResults()) {
            return List.of();
        }
        List<Integer> order = new ArrayList<>(response.results().size());
        for (RerankResult result : response.results()) {
            Integer index = result.index();
            if (index != null && index >= 0 && index < candidateCount) {
                order.add(index);
            }
        }
        return order;
    }

    /** Full ARN as-is, else expand a bare model id to the region's foundation-model ARN. */
    private String resolveModelArn(String region) {
        String configured = globalSettingsService.getRagSnRerankModel();
        String id = StringUtils.hasText(configured) ? configured.trim() : DEFAULT_MODEL_ID;
        if (id.startsWith("arn:")) {
            return id;
        }
        return "arn:aws:bedrock:" + region + "::foundation-model/" + id;
    }
}
