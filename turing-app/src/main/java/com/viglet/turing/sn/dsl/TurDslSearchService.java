package com.viglet.turing.sn.dsl;

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

    public Optional<TurDslSearchResponse> search(String siteName, String locale,
                                                   TurDslQueryRequest request) {
        Locale parsedLocale = LocaleUtils.toLocale(locale);
        return turSNSiteRepository.findByNameIgnoreCase(siteName)
                .flatMap(site -> {
                    String engineType = pluginFactory.getPluginForSite(site)
                            .getPluginType().toLowerCase();
                    TurDslSearchEngineExecutor<?, ?> executor = executorMap.getOrDefault(
                            engineType, executorMap.get("solr"));
                    if (executor == null) {
                        log.error("No DSL executor found for engine '{}' and no Solr fallback",
                                engineType);
                        return Optional.empty();
                    }
                    return executor.execute(siteName, parsedLocale, request);
                });
    }
}
