/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag.backend;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.properties.TurRetrievalProperty;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseQuery;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseRetrievalResult;
import software.amazon.awssdk.services.bedrockagentruntime.model.KnowledgeBaseVectorSearchConfiguration;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrievalResultLocation;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveRequest;
import software.amazon.awssdk.services.bedrockagentruntime.model.RetrieveResponse;

/**
 * T520 / §XXVIII.16 — the AWS Bedrock Knowledge Base retrieval backend (the
 * {@code Retrieve} API). Returns the KB's top passages as Spring AI
 * {@link Document}s carrying {@code source_id} / {@code url} / {@code title}
 * provenance, so they flow through Turing's reranking, prompt-stuffing,
 * {@code sources[]} and native-citation paths exactly like a built-in hit — i.e.
 * a Bedrock-KB passage becomes a citation source through the existing
 * {@code TurCitationDocument} mapping, no separate decoder needed.
 *
 * <p>Credentials mirror the T506 Bedrock model: static keys when configured, else
 * the AWS default credentials chain (IAM role). Inert ({@link #isAvailable()}
 * false) when no knowledge-base id is set; {@link TurRetrievalBackendResolver}
 * then leaves the built-in path running.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurBedrockKnowledgeBaseBackend implements TurRetrievalBackend {

    private static final String SOURCE_ID = "source_id";

    private final TurRetrievalProperty retrievalProperty;

    public TurBedrockKnowledgeBaseBackend(TurRetrievalProperty retrievalProperty) {
        this.retrievalProperty = retrievalProperty;
    }

    @Override
    public TurRetrievalBackendType getType() {
        return TurRetrievalBackendType.BEDROCK_KB;
    }

    @Override
    public boolean isAvailable() {
        TurRetrievalProperty.BedrockKb cfg = retrievalProperty.getBedrockKb();
        return cfg != null && StringUtils.hasText(cfg.getKnowledgeBaseId());
    }

    @Override
    public List<Document> retrieve(TurRetrievalRequest request) {
        TurRetrievalProperty.BedrockKb cfg = retrievalProperty.getBedrockKb();
        if (cfg == null || !StringUtils.hasText(cfg.getKnowledgeBaseId())) {
            return List.of();
        }
        int topK = request.topK() > 0 ? Math.min(request.topK(), 100) : 5;
        try (BedrockAgentRuntimeClient client = buildClient(cfg)) {
            RetrieveResponse response = client.retrieve(RetrieveRequest.builder()
                    .knowledgeBaseId(cfg.getKnowledgeBaseId())
                    .retrievalQuery(KnowledgeBaseQuery.builder().text(request.query()).build())
                    .retrievalConfiguration(KnowledgeBaseRetrievalConfiguration.builder()
                            .vectorSearchConfiguration(KnowledgeBaseVectorSearchConfiguration.builder()
                                    .numberOfResults(topK)
                                    .build())
                            .build())
                    .build());
            return mapResults(response);
        }
    }

    /** Maps a Bedrock KB {@code Retrieve} response into Spring AI documents.
     *  Package-private for focused unit testing of the mapping. */
    List<Document> mapResults(RetrieveResponse response) {
        if (response == null || !response.hasRetrievalResults()) {
            return List.of();
        }
        List<Document> docs = new ArrayList<>(response.retrievalResults().size());
        int i = 0;
        for (KnowledgeBaseRetrievalResult result : response.retrievalResults()) {
            String text = result.content() != null ? result.content().text() : null;
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String uri = locationUri(result.location());
            String sourceId = StringUtils.hasText(uri) ? uri : "bedrock-kb-" + i;
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(SOURCE_ID, sourceId);
            if (StringUtils.hasText(uri)) {
                metadata.put("url", uri);
                metadata.put("title", titleFromUri(uri));
            }
            docs.add(Document.builder()
                    .id(sourceId + "#" + i)
                    .text(text)
                    .metadata(metadata)
                    .score(result.score())
                    .build());
            i++;
        }
        return docs;
    }

    /** Reads the source URI out of the (union-typed) retrieval-result location. */
    private static String locationUri(RetrievalResultLocation location) {
        if (location == null) {
            return null;
        }
        if (location.s3Location() != null) {
            return location.s3Location().uri();
        }
        if (location.webLocation() != null) {
            return location.webLocation().url();
        }
        if (location.confluenceLocation() != null) {
            return location.confluenceLocation().url();
        }
        if (location.salesforceLocation() != null) {
            return location.salesforceLocation().url();
        }
        if (location.sharePointLocation() != null) {
            return location.sharePointLocation().url();
        }
        if (location.customDocumentLocation() != null) {
            return location.customDocumentLocation().id();
        }
        return null;
    }

    /** Derives a human-friendly title from a source URI (its last path segment). */
    private static String titleFromUri(String uri) {
        String trimmed = uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
        int slash = trimmed.lastIndexOf('/');
        String tail = slash >= 0 && slash + 1 < trimmed.length() ? trimmed.substring(slash + 1) : trimmed;
        return tail.isBlank() ? uri : tail;
    }

    private BedrockAgentRuntimeClient buildClient(TurRetrievalProperty.BedrockKb cfg) {
        AwsCredentialsProvider credentials;
        if (StringUtils.hasText(cfg.getAccessKeyId()) && StringUtils.hasText(cfg.getSecretAccessKey())) {
            credentials = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(cfg.getAccessKeyId(), cfg.getSecretAccessKey()));
        } else {
            credentials = DefaultCredentialsProvider.create();
        }
        return BedrockAgentRuntimeClient.builder()
                .region(Region.of(StringUtils.hasText(cfg.getRegion()) ? cfg.getRegion() : "us-east-1"))
                .credentialsProvider(credentials)
                .build();
    }
}
