package com.viglet.turing.genai.provider.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Loads the bundled, curated {@code llm/models-catalog.json} once at startup
 * and exposes it per provider plugin type. This is the static fallback for the
 * LLM-instance model picker (T577): when a live vendor query returns nothing
 * (no API key yet, offline, or a provider without a "list models" endpoint),
 * the discovery service serves these instead.
 *
 * <p>The catalog is a bundled resource — refreshed on redeploy, not at runtime
 * — so it's loaded once into an immutable map rather than cached via Spring's
 * cache abstraction.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLlmModelCatalog {

    private static final String CATALOG_RESOURCE = "llm/models-catalog.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, List<TurLlmModelOption>> catalog = Map.of();

    @PostConstruct
    void load() {
        Map<String, List<TurLlmModelOption>> parsed = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource(CATALOG_RESOURCE).getInputStream());
            root.fields().forEachRemaining(entry -> {
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
        } catch (Exception e) {
            log.warn("Could not load LLM model catalog '{}': {}", CATALOG_RESOURCE, e.getMessage());
        }
        this.catalog = Map.copyOf(parsed);
        log.info("Loaded static LLM model catalog for {} providers", catalog.size());
    }

    /** Curated models for a plugin type, or empty when none are bundled. */
    public List<TurLlmModelOption> staticModels(String pluginType) {
        if (pluginType == null) {
            return List.of();
        }
        return catalog.getOrDefault(pluginType.toLowerCase(Locale.ROOT), List.of());
    }
}
