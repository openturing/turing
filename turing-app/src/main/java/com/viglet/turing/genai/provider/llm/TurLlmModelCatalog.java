package com.viglet.turing.genai.provider.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Serves the curated model catalog per provider plugin type — the static
 * fallback for the LLM-instance model picker (T577): when a live vendor query
 * returns nothing (no API key yet, offline, or a provider without a "list
 * models" endpoint), the discovery service serves these instead.
 *
 * <p>Two sources, most-authoritative first (T762):
 * <ol>
 *   <li>the <b>public catalog API</b> ({@code turing.models-catalog.url}, default
 *       {@code https://turing.viglet.org/models/catalog.json}) — the market
 *       reference (Block BC), refreshed on a TTL so a running instance picks up
 *       new models without a redeploy;</li>
 *   <li>the <b>bundled</b> {@code llm/models-catalog.json} — always loaded at
 *       startup, the offline fallback used until (or unless) the remote fetch
 *       succeeds, so air-gapped installs and boot-time never depend on the
 *       network.</li>
 * </ol>
 *
 * <p>The remote fetch is best-effort and never fatal: any failure keeps the
 * current (bundled or last-good-remote) map. The refresh runs on a scheduler,
 * off the boot path, so startup stays instant.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLlmModelCatalog {

    private static final String CATALOG_RESOURCE = "llm/models-catalog.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The effective catalog served to callers. Set to the bundled resource at
     * startup (the offline fallback) and overwritten by the remote refresh only
     * when it succeeds with a non-empty result — so it always holds the last-good
     * map (bundled or remote), never an empty one from a failed fetch.
     */
    private volatile Map<String, List<TurLlmModelOption>> catalog = Map.of();

    @Value("${turing.models-catalog.url:https://turing.viglet.org/models/catalog.json}")
    private String remoteUrl;

    @Value("${turing.models-catalog.remote-enabled:true}")
    private boolean remoteEnabled;

    @PostConstruct
    void load() {
        Map<String, List<TurLlmModelOption>> parsed = Map.of();
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource(CATALOG_RESOURCE).getInputStream());
            parsed = parseCatalog(root);
        } catch (Exception e) {
            log.warn("Could not load bundled LLM model catalog '{}': {}", CATALOG_RESOURCE, e.getMessage());
        }
        this.catalog = parsed;
        log.info("Loaded bundled LLM model catalog for {} providers", catalog.size());
    }

    /**
     * Refreshes the effective catalog from the public endpoint on a TTL (T762).
     * Best-effort: on any failure the current map is kept. Runs off the boot path
     * (an initial delay) so startup never waits on the network.
     */
    @Scheduled(
            initialDelayString = "${turing.models-catalog.refresh-initial-delay-ms:8000}",
            fixedDelayString = "${turing.models-catalog.refresh-interval-ms:21600000}")
    void refreshFromRemote() {
        if (!remoteEnabled || !StringUtils.hasText(remoteUrl)) {
            return;
        }
        try {
            SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
            rf.setConnectTimeout(Duration.ofSeconds(3));
            rf.setReadTimeout(Duration.ofSeconds(5));
            String body = RestClient.builder().requestFactory(rf).build()
                    .get().uri(remoteUrl)
                    .retrieve()
                    .body(String.class);
            if (!StringUtils.hasText(body)) {
                return;
            }
            Map<String, List<TurLlmModelOption>> parsed = parseCatalog(objectMapper.readTree(body));
            if (!parsed.isEmpty()) {
                this.catalog = parsed;
                log.info("Refreshed LLM model catalog from {} ({} providers)", remoteUrl, parsed.size());
            }
        } catch (Exception e) {
            // Offline / unreachable / malformed — keep the current (bundled or last-good) catalog.
            log.debug("Remote LLM model catalog refresh from {} failed, keeping current: {}",
                    remoteUrl, e.getMessage());
        }
    }

    /**
     * Parses a catalog document into the vendor→models map. Accepts the T759
     * versioned envelope ({@code {version, lastUpdated, vendors:{...}}}) — as
     * served by both the bundled resource and the public endpoint — and tolerates
     * the legacy flat shape ({@code {vendor:[...]}}) where the root is the map.
     */
    Map<String, List<TurLlmModelOption>> parseCatalog(JsonNode root) {
        Map<String, List<TurLlmModelOption>> parsed = new LinkedHashMap<>();
        JsonNode vendors = root.has("vendors") ? root.get("vendors") : root;
        vendors.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value.isArray()) {
                List<TurLlmModelOption> models = new ArrayList<>();
                value.forEach(node -> {
                    String id = node.path("id").asText(null);
                    if (id != null && !id.isBlank()) {
                        String label = node.path("label").asText(id);
                        String kindOverride = node.path("kind").asText(null);
                        models.add(new TurLlmModelOption(id, label,
                                TurLlmModelKind.parseOrClassify(kindOverride, id, label)));
                    }
                });
                parsed.put(entry.getKey().toLowerCase(Locale.ROOT), List.copyOf(models));
            }
        });
        return Map.copyOf(parsed);
    }

    /** Curated models for a plugin type, or empty when none are bundled. */
    public List<TurLlmModelOption> staticModels(String pluginType) {
        if (pluginType == null) {
            return List.of();
        }
        return catalog.getOrDefault(pluginType.toLowerCase(Locale.ROOT), List.of());
    }
}
