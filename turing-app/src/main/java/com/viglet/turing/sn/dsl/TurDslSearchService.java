package com.viglet.turing.sn.dsl;

import com.viglet.turing.api.exception.TurUnsupportedEngineException;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.LocaleUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrator service for DSL search queries.
 * <p>
 * Resolves the search engine type from the SN site configuration and
 * delegates execution to the appropriate {@link TurDslSearchEngineExecutor}
 * strategy implementation (Solr or Lucene).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Service
public class TurDslSearchService {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final Map<String, TurDslSearchEngineExecutor<?, ?>> executorMap;

    public TurDslSearchService(TurSNSiteRepository turSNSiteRepository,
                               TurSearchEnginePluginFactory pluginFactory,
                               List<TurDslSearchEngineExecutor<?, ?>> executors) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.pluginFactory = pluginFactory;
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(
                        e -> e.getEngineType().toLowerCase(),
                        Function.identity()));
        log.info("DSL search executors registered: {}", executorMap.keySet());
    }

    /**
     * Runs a DSL query against whichever engine the site is bound to.
     *
     * <p>T1046 / §XX.23 — the executor is resolved by <em>exact</em> engine type,
     * with no fallback. The previous
     * {@code getOrDefault(engineType, executorMap.get("solr"))} was copied from
     * {@link TurSearchEnginePluginFactory#getPlugin(String)}, where falling back is
     * defensible because every plugin implements one interface and so answers the
     * same questions. A DSL executor translates to a <em>native</em> query
     * language, which is engine-specific by definition: for an engine with no
     * executor the fallback was not a degradation but the wrong engine.
     *
     * <p>What that cost, concretely: {@code TurDslSolrExecutor.execute} calls
     * {@code initSolrInstance(siteName, locale)}, which builds a Solr client from
     * the <em>site's own</em> SE instance endpoint URL. On a Content AI site
     * (T1031) that URL is Adobe's, so Turing issued a Solr query against Adobe's
     * REST API — and the executor catches {@code Exception} and returns
     * {@code Optional.empty()}, which the controller rendered as 404. So a
     * capability gap arrived as "site not found", after a wrong request to a
     * third-party API.
     *
     * @throws TurUnsupportedEngineException when no executor serves the site's
     *                                       engine, carrying the engine name and
     *                                       the engines that do (HTTP 501)
     */
    public Optional<TurDslSearchResponse> search(String siteName, String locale,
                                                   TurDslQueryRequest request) {
        Locale parsedLocale = LocaleUtils.toLocale(locale);
        return turSNSiteRepository.findByNameIgnoreCase(siteName)
                .flatMap(site -> {
                    String engineType = pluginFactory.getPluginForSite(site)
                            .getPluginType().toLowerCase();
                    TurDslSearchEngineExecutor<?, ?> executor = executorMap.get(engineType);
                    if (executor == null) {
                        log.error("Site '{}' uses the '{}' engine, which has no DSL executor; "
                                + "refusing rather than dispatching to another engine. "
                                + "Registered: {}", siteName, engineType, executorMap.keySet());
                        throw new TurUnsupportedEngineException("The DSL query API", engineType,
                                new java.util.TreeSet<>(executorMap.keySet()));
                    }
                    return executor.execute(siteName, parsedLocale, request);
                });
    }

}
