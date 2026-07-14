package com.viglet.turing.genai.provider.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Shared, resilient HTTP helpers that query a vendor's "list models" endpoint
 * for the LLM-instance model picker (T577).
 *
 * <p>These deliberately use a plain {@link RestClient} (the same approach the
 * Ollama provider already uses for {@code fetchContextWindow}) rather than each
 * vendor SDK, so listing works uniformly across the OpenAI family, Anthropic
 * and Gemini without depending on SDK-specific "models" bindings. Every method
 * swallows failures and returns an empty list — the discovery service treats
 * empty as "fall back to the static catalog".
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public final class TurLlmModelListingSupport {

    private TurLlmModelListingSupport() {
    }

    /**
     * OpenAI-compatible {@code GET {baseUrl}/models} with a Bearer token.
     * Response shape: {@code {"data":[{"id":"..."}, ...]}}. Used by OpenAI,
     * the generic OpenAI-compatible provider, Gemini-OpenAI, Cohere and
     * Mistral (all speak the same endpoint).
     */
    public static List<TurLlmModelOption> openAiStyle(String baseUrl, String apiKey) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey)) {
            return List.of();
        }
        try {
            Map<String, Object> body = RestClient.create(normalize(baseUrl)).get()
                    .uri("/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(Map.class);
            return extractFromData(body);
        } catch (Exception e) {
            log.debug("Could not list models from OpenAI-style endpoint {}: {}", baseUrl, e.getMessage());
            return List.of();
        }
    }

    /**
     * Anthropic {@code GET {baseUrl}/v1/models} — auth via {@code x-api-key}
     * plus the required {@code anthropic-version} header. Response shape:
     * {@code {"data":[{"id":"...","display_name":"..."}, ...]}}.
     */
    public static List<TurLlmModelOption> anthropic(String baseUrl, String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return List.of();
        }
        String base = StringUtils.hasText(baseUrl) ? baseUrl : "https://api.anthropic.com";
        try {
            Map<String, Object> body = RestClient.create(normalize(base)).get()
                    .uri("/v1/models?limit=1000")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .retrieve()
                    .body(Map.class);
            return extractFromData(body);
        } catch (Exception e) {
            log.debug("Could not list Anthropic models: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Gemini (native GenAI REST) {@code GET {baseUrl}/models?key=...}.
     * Response shape: {@code {"models":[{"name":"models/gemini-..."}, ...]}}.
     * The {@code models/} prefix is stripped so the stored id matches what
     * the GenAI SDK expects.
     */
    public static List<TurLlmModelOption> gemini(String baseUrl, String apiKey) {
        if (!StringUtils.hasText(apiKey)) {
            return List.of();
        }
        String base = ensureGeminiApiVersion(
                StringUtils.hasText(baseUrl) ? baseUrl : "https://generativelanguage.googleapis.com/v1beta");
        try {
            Map<String, Object> body = RestClient.create(normalize(base)).get()
                    .uri("/models?pageSize=1000&key={key}", apiKey)
                    .retrieve()
                    .body(Map.class);
            if (body != null && body.get("models") instanceof List<?> models) {
                List<TurLlmModelOption> result = new ArrayList<>();
                for (Object item : models) {
                    if (item instanceof Map<?, ?> model) {
                        String name = stripPrefix(String.valueOf(model.get("name")), "models/");
                        String label = model.get("displayName") != null
                                ? String.valueOf(model.get("displayName")) : name;
                        if (StringUtils.hasText(name)) {
                            result.add(new TurLlmModelOption(name, label));
                        }
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.debug("Could not list Gemini models: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Ollama {@code GET {baseUrl}/api/tags} — lists locally installed models.
     * Response shape: {@code {"models":[{"name":"llama3.2:latest"}, ...]}}.
     */
    public static List<TurLlmModelOption> ollama(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return List.of();
        }
        try {
            Map<String, Object> body = RestClient.create(normalize(baseUrl)).get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(Map.class);
            if (body != null && body.get("models") instanceof List<?> models) {
                List<TurLlmModelOption> result = new ArrayList<>();
                for (Object item : models) {
                    if (item instanceof Map<?, ?> model) {
                        String name = String.valueOf(model.get("name"));
                        if (StringUtils.hasText(name)) {
                            result.add(TurLlmModelOption.of(name));
                        }
                    }
                }
                return result;
            }
        } catch (Exception e) {
            log.debug("Could not list Ollama models from {}: {}", baseUrl, e.getMessage());
        }
        return List.of();
    }

    /** Parses the common {@code {"data":[{"id":..., "display_name"/"name":...}]}} shape. */
    @SuppressWarnings("unchecked")
    private static List<TurLlmModelOption> extractFromData(Map<String, Object> body) {
        if (body == null || !(body.get("data") instanceof List<?> data)) {
            return List.of();
        }
        List<TurLlmModelOption> result = new ArrayList<>();
        for (Object item : data) {
            if (item instanceof Map<?, ?> model) {
                String id = String.valueOf(model.get("id"));
                if (!StringUtils.hasText(id) || "null".equals(id)) {
                    continue;
                }
                Object display = model.get("display_name") != null ? model.get("display_name") : model.get("name");
                String label = display != null ? String.valueOf(display) : id;
                result.add(new TurLlmModelOption(id, label));
            }
        }
        return result;
    }

    /** Trims a trailing slash so {@code baseUrl + "/models"} never double-slashes. */
    private static String normalize(String baseUrl) {
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    /**
     * The Gemini REST "list models" call needs an explicit API-version segment
     * ({@code /v1beta}). A chat instance may store only the bare host
     * ({@code https://generativelanguage.googleapis.com}) because the GenAI SDK
     * appends the version itself — so append {@code /v1beta} when the base has
     * no version segment, leaving proxy/custom bases that already carry one
     * (e.g. {@code /v1beta}, {@code /v1alpha}) untouched.
     */
    private static String ensureGeminiApiVersion(String base) {
        String normalized = normalize(base);
        return normalized.contains("/v1") ? normalized : normalized + "/v1beta";
    }

    private static String stripPrefix(String value, String prefix) {
        if (value != null && value.startsWith(prefix)) {
            return value.substring(prefix.length());
        }
        return value;
    }
}
