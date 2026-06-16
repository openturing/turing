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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the {@code viglet-core} settings store, bound from
 * {@code viglet.settings.*}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ConfigurationProperties(prefix = "viglet.settings")
public class VigletSettingsProperties {

    /**
     * The {@code path} stamped on every config-var written through the
     * auto-configured {@link VigletConfigVarService} when no explicit path is
     * passed. Lets a product group its global settings under one logical path
     * (e.g. {@code "/system/global"}).
     */
    private String defaultPath = "/system";

    public String getDefaultPath() {
        return defaultPath;
    }

    public void setDefaultPath(String defaultPath) {
        this.defaultPath = defaultPath;
    }
}
