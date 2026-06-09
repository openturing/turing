package com.viglet.turing.genai.provider.llm;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.http.MediaType;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurOllamaLlmProvider implements TurGenAiLlmProvider {

    private static final String BASE_URL = "baseUrl";
    private static final String MODEL = "model";
    private final TurProviderOptionsParser optionsParser;

    public TurOllamaLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "ollama";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String modelName = firstNonBlank(
                optionsParser.stringValue(options, MODEL),
                turLLMInstance.getModelName());
        if (!StringUtils.hasText(modelName)) {
            throw new IllegalStateException("Missing model name for Ollama instance: " + turLLMInstance.getId());
        }

        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(firstNonBlank(optionsParser.stringValue(options, BASE_URL), turLLMInstance.getUrl()))
                .build();

        OllamaChatOptions.Builder optionsBuilder = OllamaChatOptions.builder()
                .model(modelName);

        Double temperature = firstNonNull(optionsParser.doubleValue(options, "temperature"),
                turLLMInstance.getTemperature());
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        Integer topK = firstNonNull(optionsParser.intValue(options, "topK"), turLLMInstance.getTopK());
        if (topK != null) {
            optionsBuilder.topK(topK);
        }
        Double topP = firstNonNull(optionsParser.doubleValue(options, "topP"), turLLMInstance.getTopP());
        if (topP != null) {
            optionsBuilder.topP(topP);
        }
        Double repeatPenalty = firstNonNull(optionsParser.doubleValue(options, "repeatPenalty"),
                turLLMInstance.getRepeatPenalty());
        if (repeatPenalty != null) {
            optionsBuilder.repeatPenalty(repeatPenalty);
        }
        Integer seed = firstNonNull(optionsParser.intValue(options, "seed"), turLLMInstance.getSeed());
        if (seed != null) {
            optionsBuilder.seed(seed);
        }
        Integer numPredict = firstNonNull(optionsParser.intValue(options, "numPredict"),
                turLLMInstance.getNumPredict());
        if (numPredict != null) {
            optionsBuilder.numPredict(numPredict);
        }

        List<String> stopSequences = optionsParser.stringListValue(options, "stop");
        if (stopSequences.isEmpty()) {
            stopSequences = parseStopSequences(turLLMInstance.getStop());
        }
        if (!stopSequences.isEmpty()) {
            optionsBuilder.stop(stopSequences);
        }

        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(optionsBuilder.build())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String modelName = firstNonBlank(
                optionsParser.stringValue(options, "embeddingModel"),
                optionsParser.stringValue(options, MODEL),
                turLLMInstance.getModelName());
        if (!StringUtils.hasText(modelName)) {
            throw new IllegalStateException(
                    "Missing embedding model name for Ollama instance: " + turLLMInstance.getId());
        }

        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(firstNonBlank(optionsParser.stringValue(options, BASE_URL), turLLMInstance.getUrl()))
                .build();

        OllamaEmbeddingOptions embeddingOptions = OllamaEmbeddingOptions.builder()
                .model(modelName)
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .options(embeddingOptions)
                .build();
    }

    @Override
    public OptionalInt fetchContextWindow(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());
        String baseUrl = firstNonBlank(optionsParser.stringValue(options, BASE_URL), turLLMInstance.getUrl());
        String modelName = firstNonBlank(
                optionsParser.stringValue(options, MODEL),
                turLLMInstance.getModelName());

        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(modelName)) {
            return OptionalInt.empty();
        }

        RestClient client = RestClient.create(baseUrl);

        // 1) Try /api/ps — returns the effective context_length for running models
        // (reflects Ollama Settings override like num_ctx)
        OptionalInt fromPs = fetchFromRunningModels(client, modelName);
        if (fromPs.isPresent()) {
            return fromPs;
        }

        // 2) Fallback to /api/show — returns the model's maximum capability
        return fetchFromModelInfo(client, modelName);
    }

    private OptionalInt fetchFromRunningModels(RestClient client, String modelName) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.get()
                    .uri("/api/ps")
                    .retrieve()
                    .body(Map.class);

            if (body != null && body.get("models") instanceof List<?> models) {
                for (Object item : models) {
                    if (item instanceof Map<?, ?> model) {
                        OptionalInt result = extractContextLength(model, modelName);
                        if (result.isPresent()) {
                            return result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Could not query Ollama /api/ps: {}", e.getMessage());
        }
        return OptionalInt.empty();
    }

    private OptionalInt extractContextLength(Map<?, ?> model, String modelName) {
        String name = String.valueOf(model.get("name"));
        // Match by model name (with or without :tag suffix)
        if (name.equals(modelName)
                || name.startsWith(modelName + ":")
                || modelName.equals(String.valueOf(model.get(MODEL)))) {
            Object ctxLen = model.get("context_length");
            if (ctxLen instanceof Number num && num.intValue() > 0) {
                return OptionalInt.of(num.intValue());
            }
        }
        return OptionalInt.empty();
    }

    private OptionalInt fetchFromModelInfo(RestClient client, String modelName) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.post()
                    .uri("/api/show")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("name", modelName))
                    .retrieve()
                    .body(Map.class);

            if (body != null && body.get("model_info") instanceof Map<?, ?> modelInfo) {
                for (Map.Entry<?, ?> entry : modelInfo.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (key.endsWith(".context_length") && entry.getValue() instanceof Number num) {
                        return OptionalInt.of(num.intValue());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch context window from Ollama for model '{}': {}", modelName, e.getMessage());
        }
        return OptionalInt.empty();
    }

    private List<String> parseStopSequences(String rawStop) {
        if (!StringUtils.hasText(rawStop)) {
            return List.of();
        }
        return Arrays.stream(rawStop.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    @Nullable
    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
