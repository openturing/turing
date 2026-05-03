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

import com.viglet.turing.observability.TurSearchObservation;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.resilience.TurResilienceExecutor;
import com.viglet.turing.resilience.TurResilienceRegistry;
import com.viglet.turing.resilience.TurResilienceRegistry.Kind;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private static final Set<String> NON_IO_METHODS = Set.of(
            "getPluginType",
            "toString",
            "hashCode",
            "equals");

    private final Map<String, TurSearchEnginePlugin> pluginMap;
    private final TurResilienceExecutor resilienceExecutor;
    private final TurResilienceRegistry resilienceRegistry;
    private final TurSearchObservation searchObservation;

    @org.springframework.beans.factory.annotation.Autowired
    public TurSearchEnginePluginFactory(List<TurSearchEnginePlugin> plugins,
            TurResilienceExecutor resilienceExecutor,
            TurResilienceRegistry resilienceRegistry,
            TurSearchObservation searchObservation) {
        this.resilienceExecutor = resilienceExecutor;
        this.resilienceRegistry = resilienceRegistry;
        this.searchObservation = searchObservation;
        this.pluginMap = plugins.stream()
                .collect(Collectors.toMap(
                        plugin -> plugin.getPluginType().toLowerCase(),
                        this::wrapWithResilience,
                        (a, b) -> a));
        log.info("Initialized TurSearchEnginePluginFactory with {} plugins (resilience pipeline {}, observation {}).",
                pluginMap.size(),
                resilienceRegistry == null ? "disabled" : "applied",
                searchObservation == null ? "disabled" : "applied");
        pluginMap.keySet().forEach(type -> log.info("Registered plugin: {}", type));
    }

    /**
     * Test-only constructor — resilience pipeline and observation are bypassed
     * since their dependencies are absent. Production code goes through Spring
     * DI which always provides the multi-arg constructor.
     */
    public TurSearchEnginePluginFactory(List<TurSearchEnginePlugin> plugins) {
        this(plugins, null, null, null);
    }

    /**
     * Wraps a plugin with a JDK dynamic proxy that routes every IO-bound method
     * call through the resilience executor (retry + circuit breaker + time
     * limiter) and then through the observation layer (timer + span). Methods
     * that don't perform IO are excluded so they don't pollute circuit-breaker
     * statistics or span volume. When dependencies are absent (test setup) the
     * delegate is returned unwrapped.
     */
    private TurSearchEnginePlugin wrapWithResilience(TurSearchEnginePlugin delegate) {
        if (resilienceExecutor == null || resilienceRegistry == null) {
            return delegate;
        }
        String pluginType = delegate.getPluginType();
        InvocationHandler handler = (proxy, method, args) -> {
            if (NON_IO_METHODS.contains(method.getName())
                    || !resilienceRegistry.isEnabled(Kind.SEARCH_ENGINE)) {
                return invokeRaw(delegate, method, args);
            }
            String operation = method.getName();
            java.util.function.Supplier<Object> resilient = () ->
                    resilienceExecutor.execute(Kind.SEARCH_ENGINE, pluginType, () -> {
                        try {
                            return invokeRaw(delegate, method, args);
                        } catch (RuntimeException re) {
                            throw re;
                        } catch (Throwable t) {
                            throw new InvocationWrapper(t);
                        }
                    });
            return searchObservation != null
                    ? searchObservation.observeCall(pluginType, operation, resilient)
                    : resilient.get();
        };
        return (TurSearchEnginePlugin) Proxy.newProxyInstance(
                delegate.getClass().getClassLoader(),
                new Class<?>[] { TurSearchEnginePlugin.class },
                handler);
    }

    private static Object invokeRaw(TurSearchEnginePlugin delegate, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException ite) {
            throw ite.getTargetException();
        }
    }

    /** Carries checked exceptions so the resilience executor can surface them as RuntimeExceptions. */
    private static final class InvocationWrapper extends RuntimeException {
        InvocationWrapper(Throwable cause) {
            super(cause);
        }
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
