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
package com.viglet.core.settings;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures a {@link VigletConfigVarService} from {@code viglet.settings.*}
 * <em>only when</em> the product publishes a {@link VigletConfigVarStore} bean —
 * the module is inert until a product supplies its persistence adapter, and a
 * product that wires its own {@code VigletConfigVarService} (e.g. with a
 * product-specific default path) wins via {@link ConditionalOnMissingBean}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@AutoConfiguration
@EnableConfigurationProperties(VigletSettingsProperties.class)
public class VigletSettingsAutoConfiguration {

    @Bean
    @ConditionalOnBean(VigletConfigVarStore.class)
    @ConditionalOnMissingBean
    public VigletConfigVarService vigletConfigVarService(VigletConfigVarStore store,
            VigletSettingsProperties properties) {
        return new VigletConfigVarService(store, properties.getDefaultPath());
    }
}
