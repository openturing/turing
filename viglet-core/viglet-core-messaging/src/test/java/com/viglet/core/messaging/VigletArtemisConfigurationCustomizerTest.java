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
package com.viglet.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.jupiter.api.Test;

class VigletArtemisConfigurationCustomizerTest {

    @Test
    void shouldApplyDefaultTuningToNamedAndCatchAllAddresses() {
        ConfigurationImpl configuration = new ConfigurationImpl();
        VigletArtemisConfigurationCustomizer customizer =
                new VigletArtemisConfigurationCustomizer("indexing.queue");

        customizer.customize(configuration);

        assertThat(configuration.getGlobalMaxSize())
                .isEqualTo(VigletArtemisConfigurationCustomizer.DEFAULT_GLOBAL_MAX_SIZE);
        assertThat(configuration.getMaxDiskUsage())
                .isEqualTo(VigletArtemisConfigurationCustomizer.DEFAULT_MAX_DISK_USAGE);

        AddressSettings named = configuration.getAddressSettings().get("indexing.queue");
        assertThat(named).isNotNull();
        assertThat(named.getAddressFullMessagePolicy()).isEqualTo(AddressFullMessagePolicy.PAGE);
        assertThat(named.getMaxSizeBytes())
                .isEqualTo(VigletArtemisConfigurationCustomizer.DEFAULT_ADDRESS_MAX_SIZE_BYTES);
        assertThat(named.getPageSizeBytes())
                .isEqualTo(VigletArtemisConfigurationCustomizer.DEFAULT_PAGE_SIZE_BYTES);

        assertThat(configuration.getAddressSettings()).containsKey("#");
    }

    @Test
    void shouldHonorExplicitLimits() {
        ConfigurationImpl configuration = new ConfigurationImpl();
        VigletArtemisConfigurationCustomizer customizer =
                new VigletArtemisConfigurationCustomizer(2048L, 80, 256L, 4096, "a", "b");

        customizer.customize(configuration);

        assertThat(configuration.getGlobalMaxSize()).isEqualTo(2048L);
        assertThat(configuration.getMaxDiskUsage()).isEqualTo(80);
        assertThat(configuration.getAddressSettings()).containsKeys("a", "b", "#");
        assertThat(configuration.getAddressSettings().get("a").getMaxSizeBytes()).isEqualTo(256L);
    }

    @Test
    void shouldTolerateNoNamedAddresses() {
        ConfigurationImpl configuration = new ConfigurationImpl();

        new VigletArtemisConfigurationCustomizer().customize(configuration);

        assertThat(configuration.getAddressSettings()).containsKey("#");
    }
}
