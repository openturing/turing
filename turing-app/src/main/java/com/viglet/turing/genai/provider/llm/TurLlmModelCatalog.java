package com.viglet.turing.genai.provider.llm;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Serves the curated model catalog per provider plugin type — the static
 * fallback for the LLM-instance model picker (T577): when a live vendor query
 * returns nothing (no API key yet, offline, or a provider without a "list
 * models" endpoint), the discovery service serves these instead.
 *
 * <p>The catalog lives in its own repository — <b>{@code openviglet/models-catalog}</b>
 * — which publishes it as a public, CORS-open JSON endpoint. Turing fetches it
 * from {@code turing.models-catalog.url} (default the published rolling endpoint)
 * on a TTL, so a running instance picks up new models without a redeploy.
 *
 * <p>There is <b>no bundled offline fallback</b>: the catalog is remote-only. The
 * effective map is empty until the first successful refresh, after which it holds
 * the last-good remote result (a failed fetch keeps the current map, never an
 * empty one). The refresh runs on a scheduler, off the boot path, so startup
 * never waits on the network; with {@code remote-enabled=false} the catalog stays
 * empty and callers fall back to live vendor listing only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLlmModelCatalog {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The effective catalog served to callers. Empty until the first successful
     * remote refresh; thereafter it holds the last-good remote map (a failed fetch
     * keeps the current map, never an empty one from a failed request).
     */
    private volatile Map<String, List<TurLlmModelOption>> catalog = Map.of();

    @Value("${turing.models-catalog.url:https://openviglet.github.io/models-catalog/catalog.json}")
    private String remoteUrl;

    @Value("${turing.models-catalog.remote-enabled:true}")
    private boolean remoteEnabled;

    /**
     * Refreshes the effective catalog from the public endpoint on a TTL. This is
     * the only source (remote-only). Best-effort: on any failure the current map
     * is kept. Runs off the boot path (an initial delay) so startup never waits on
     * the network.
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
            // Offline / unreachable / malformed — keep the current (last-good) catalog.
            log.debug("Remote LLM model catalog refresh from {} failed, keeping current: {}",
                    remoteUrl, e.getMessage());
        }
    }

    /**
     * Parses a catalog document into the vendor→models map. Accepts the canonical
     * versioned envelope ({@code {version, lastUpdated, vendors:{...}}}) — as served
     * by the public endpoint — and tolerates the legacy flat shape
     * ({@code {vendor:[...]}}) where the root is the map.
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

    /** Curated models for a plugin type, or empty when none are available yet. */
    public List<TurLlmModelOption> staticModels(String pluginType) {
        if (pluginType == null) {
            return List.of();
        }
        return catalog.getOrDefault(pluginType.toLowerCase(Locale.ROOT), List.of());
    }
}
