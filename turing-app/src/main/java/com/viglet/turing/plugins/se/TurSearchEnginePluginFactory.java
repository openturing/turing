/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.plugins.se;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for selecting search engine plugins based on the site's SE instance vendor.
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
@Slf4j
@Component
public class TurSearchEnginePluginFactory {

    private final Map<String, TurSearchEnginePlugin> pluginMap;

    public TurSearchEnginePluginFactory(List<TurSearchEnginePlugin> plugins) {
        this.pluginMap = plugins.stream()
                .collect(Collectors.toMap(
                        plugin -> plugin.getPluginType().toLowerCase(),
                        Function.identity()));
        log.info("Initialized TurSearchEnginePluginFactory with {} plugins.", pluginMap.size());
        pluginMap.keySet().forEach(type -> log.info("Registered plugin: {}", type));
    }

    /**
     * Resolves the search engine plugin for the given SN site by inspecting its
     * SE instance vendor plugin type.
     *
     * @param turSNSite the SN site whose SE instance defines the engine
     * @return the matching plugin, or Solr as fallback
     */
    public TurSearchEnginePlugin getPluginForSite(TurSNSite turSNSite) {
        String pluginType = Optional.ofNullable(turSNSite)
                .map(TurSNSite::getTurSEInstance)
                .map(i -> i.getTurSEVendor())
                .map(v -> StringUtils.isNotBlank(v.getPlugin()) ? v.getPlugin() : v.getId())
                .orElse("solr");
        return getPlugin(pluginType);
    }

    /**
     * Resolves the search engine plugin for the given SE instance by inspecting its vendor plugin type.
     *
     * @param turSEInstance the SE instance whose vendor defines the engine
     * @return the matching plugin, or Solr as fallback
     */
    public TurSearchEnginePlugin getPluginForInstance(TurSEInstance turSEInstance) {
        String pluginType = Optional.ofNullable(turSEInstance)
                .map(TurSEInstance::getTurSEVendor)
                .map(v -> StringUtils.isNotBlank(v.getPlugin()) ? v.getPlugin() : v.getId())
                .orElse("solr");
        return getPlugin(pluginType);
    }

    /**
     * Gets a specific search engine plugin by type.
     *
     * @param engineType the type of search engine (e.g., "solr", "lucene")
     * @return the search engine plugin
     * @throws IllegalStateException if the plugin is not found
     */
    public TurSearchEnginePlugin getPlugin(String engineType) {
        TurSearchEnginePlugin plugin = pluginMap.get(engineType.toLowerCase());
        if (plugin == null) {
            log.warn("Search engine plugin '{}' not found, falling back to 'solr'. Available: {}",
                    engineType, pluginMap.keySet());
            plugin = pluginMap.get("solr");
            if (plugin == null) {
                throw new IllegalStateException(
                        "Search engine plugin '" + engineType + "' not found and no 'solr' fallback available. " +
                        "Available plugins: " + pluginMap.keySet());
            }
        }
        return plugin;
    }
}
