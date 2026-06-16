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
package com.viglet.core.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configures the active {@link VigletStorageService} from
 * {@code viglet.storage.type}. Activates only when that property is set, so a
 * product using its own property namespace (e.g. Turing's {@code turing.storage.*},
 * which constructs the backend itself) is unaffected; {@link ConditionalOnMissingBean}
 * lets any explicit {@code VigletStorageService} bean win regardless.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@AutoConfiguration
@EnableConfigurationProperties(VigletStorageProperties.class)
@ConditionalOnProperty(prefix = "viglet.storage", name = "type")
public class VigletStorageAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VigletStorageAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public VigletStorageService vigletStorageService(VigletStorageProperties properties) {
        VigletStorageType type = properties.getType() != null ? properties.getType() : VigletStorageType.NONE;
        log.info("Storage backend: {}", type);
        return switch (type) {
            case MINIO -> new VigletMinioStorageService(properties);
            case FILESYSTEM -> new VigletFilesystemStorageService(properties);
            case NONE -> new VigletNoOpStorageService();
        };
    }
}
