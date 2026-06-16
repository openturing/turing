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

import java.time.Duration;

import org.junit.jupiter.api.Test;

class VigletHttpClientSettingsTest {

    @Test
    void defaultsMatchTheHandTunedProductValues() {
        VigletHttpClientSettings settings = VigletHttpClientSettings.defaults();

        assertThat(settings.maxTotal()).isEqualTo(100);
        assertThat(settings.maxPerRoute()).isEqualTo(20);
        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.responseTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void floorsNonPositivePoolSizesAndDefaultsBlankTimeouts() {
        VigletHttpClientSettings settings = new VigletHttpClientSettings(0, -3, null, Duration.ofSeconds(-1));

        assertThat(settings.maxTotal()).isEqualTo(1);
        assertThat(settings.maxPerRoute()).isEqualTo(1);
        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(settings.responseTimeout()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void keepsExplicitPositiveValues() {
        VigletHttpClientSettings settings = new VigletHttpClientSettings(50, 10, Duration.ofSeconds(3),
                Duration.ofSeconds(15));

        assertThat(settings.maxTotal()).isEqualTo(50);
        assertThat(settings.maxPerRoute()).isEqualTo(10);
        assertThat(settings.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(settings.responseTimeout()).isEqualTo(Duration.ofSeconds(15));
    }
}
