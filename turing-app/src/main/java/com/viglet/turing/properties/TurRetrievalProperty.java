/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * T520 / §XXVIII.16 — configuration for the managed-RAG retrieval backend
 * ({@code turing.retrieval.*}). A deploy-time infra choice (like
 * {@code turing.storage.*}): which backend serves RAG passages. Default
 * {@code BUILT_IN} leaves the retrieval core byte-for-byte unchanged; set
 * {@code backend=BEDROCK_KB} + a knowledge-base id to route retrieval at an AWS
 * Bedrock Knowledge Base instead.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "turing.retrieval")
public class TurRetrievalProperty {

    /** Which retrieval backend serves passages: {@code BUILT_IN} (default) | {@code BEDROCK_KB}. */
    private String backend = "BUILT_IN";

    /** AWS Bedrock Knowledge Base settings ({@code turing.retrieval.bedrock-kb.*}). */
    private BedrockKb bedrockKb = new BedrockKb();

    @Getter
    @Setter
    public static class BedrockKb {
        /** The Knowledge Base id to retrieve from. Blank → backend inert (falls back to built-in). */
        private String knowledgeBaseId;
        /** AWS region of the Knowledge Base. */
        private String region = "us-east-1";
        /** Static access key id; blank → AWS default credentials chain (IAM role). */
        private String accessKeyId;
        /** Static secret access key; paired with {@link #accessKeyId}. */
        private String secretAccessKey;
    }
}
