/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.provider.llm;

import java.util.Map;

import org.springframework.util.StringUtils;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * T506 / §XXVIII.2 — resolves AWS region + credentials for the Bedrock provider
 * from a {@link TurLLMInstance}.
 *
 * <p>Bedrock is the one provider authenticated by <b>IAM</b>, not an API key, so
 * the credential shape differs from every other provider:
 * <ul>
 *   <li><b>region</b> — from the {@code region} provider option (default
 *       {@code us-east-1}).</li>
 *   <li><b>explicit static keys</b> — when an {@code accessKeyId} provider option
 *       is set, the secret access key is read from the instance's encrypted API
 *       key field (reusing the existing secret-at-rest plumbing), with an
 *       optional {@code sessionToken} provider option for STS credentials.</li>
 *   <li><b>IAM role / environment</b> — when no explicit key is configured, falls
 *       back to the {@link DefaultCredentialsProvider} chain (env vars, EC2/ECS
 *       instance profile, IAM role for service accounts), which is the path
 *       AWS-standardized enterprises actually want.</li>
 * </ul>
 *
 * @param region              the resolved AWS region
 * @param credentialsProvider the resolved credentials provider
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurBedrockCredentials(Region region, AwsCredentialsProvider credentialsProvider) {

    private static final String DEFAULT_REGION = "us-east-1";

    /**
     * Builds the region + credentials provider for {@code instance}.
     *
     * @param instance         the LLM instance
     * @param decryptedApiKey  the decrypted secret access key (may be blank when
     *                         using the default IAM chain)
     * @param optionsParser    the shared provider-options parser
     */
    public static TurBedrockCredentials from(TurLLMInstance instance, String decryptedApiKey,
            TurProviderOptionsParser optionsParser) {
        Map<String, Object> options = optionsParser.parse(instance.getProviderOptionsJson());

        String regionName = firstNonBlank(
                optionsParser.stringValue(options, "region"),
                optionsParser.stringValue(options, "awsRegion"));
        Region region = Region.of(StringUtils.hasText(regionName) ? regionName : DEFAULT_REGION);

        String accessKeyId = firstNonBlank(
                optionsParser.stringValue(options, "accessKeyId"),
                optionsParser.stringValue(options, "awsAccessKeyId"));
        String secretAccessKey = firstNonBlank(
                optionsParser.stringValue(options, "secretAccessKey"),
                decryptedApiKey);
        String sessionToken = firstNonBlank(
                optionsParser.stringValue(options, "sessionToken"),
                optionsParser.stringValue(options, "awsSessionToken"));

        AwsCredentialsProvider provider;
        if (StringUtils.hasText(accessKeyId) && StringUtils.hasText(secretAccessKey)) {
            provider = StringUtils.hasText(sessionToken)
                    ? StaticCredentialsProvider.create(
                            AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken))
                    : StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        } else {
            // No explicit keys → IAM role / environment / instance-profile chain.
            provider = DefaultCredentialsProvider.create();
        }
        return new TurBedrockCredentials(region, provider);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
