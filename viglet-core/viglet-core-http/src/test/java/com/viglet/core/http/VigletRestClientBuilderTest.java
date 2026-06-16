/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class VigletRestClientBuilderTest {

    @Test
    void buildsRestClientWithApiKeyHeaderAndTracing() {
        RestClient client = VigletRestClientBuilder.create()
                .baseUrl("http://localhost:2700")
                .apiKeyHeader("Key", "secret-token")
                .tracing(true)
                .build();

        assertThat(client).isNotNull();
    }

    @Test
    void buildsWithoutTracingOrHeaderWhenNotConfigured() {
        RestClient client = VigletRestClientBuilder.create()
                .baseUrl("http://localhost:2700")
                .build();

        assertThat(client).isNotNull();
    }

    @Test
    void ignoresBlankApiKeyHeader() {
        RestClient.Builder builder = VigletRestClientBuilder.create()
                .baseUrl("http://localhost:2700")
                .apiKeyHeader("Key", "  ")
                .builder();

        assertThat(builder).isNotNull();
    }

    @Test
    void exposesBuilderForFurtherCustomization() {
        RestClient.Builder builder = VigletRestClientBuilder.create()
                .settings(VigletHttpClientSettings.defaults())
                .builder();

        assertThat(builder.build()).isNotNull();
    }
}
