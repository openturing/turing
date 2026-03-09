package com.viglet.turing.genai.provider.llm;

import java.util.Map;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiChatOptions;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.viglet.turing.genai.provider.TurProviderOptionsParser;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;

@Component
public class TurAzureOpenAiLlmProvider implements TurGenAiLlmProvider {

    private static final String DEFAULT_CHAT_MODEL = "gpt-4o";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-ada-002";

    private final TurProviderOptionsParser optionsParser;

    public TurAzureOpenAiLlmProvider(TurProviderOptionsParser optionsParser) {
        this.optionsParser = optionsParser;
    }

    @Override
    public String getPluginType() {
        return "azure-openai";
    }

    @Override
    public ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());

        AzureOpenAiChatOptions.Builder optionsBuilder = AzureOpenAiChatOptions.builder()
                .deploymentName(resolveChatModelName(firstNonBlank(
                        optionsParser.stringValue(options, "deploymentName"),
                        optionsParser.stringValue(options, "chatModel"),
                        optionsParser.stringValue(options, "model"),
                        turLLMInstance.getModelName())));

        Double temperature = firstNonNull(optionsParser.doubleValue(options, "temperature"),
                turLLMInstance.getTemperature());
        if (temperature != null) {
            optionsBuilder.temperature(temperature);
        }
        Double topP = firstNonNull(optionsParser.doubleValue(options, "topP"), turLLMInstance.getTopP());
        if (topP != null) {
            optionsBuilder.topP(topP);
        }
        Integer seed = firstNonNull(optionsParser.intValue(options, "seed"), turLLMInstance.getSeed());
        if (seed != null) {
            optionsBuilder.seed((long) seed);
        }
        Integer maxTokens = optionsParser.intValue(options, "maxTokens");
        if (maxTokens != null) {
            optionsBuilder.maxTokens(maxTokens);
        }

        return AzureOpenAiChatModel.builder()
                .openAIClientBuilder(createClientBuilder(options, turLLMInstance, decryptedApiKey))
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    @Override
    public EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        Map<String, Object> options = optionsParser.parse(turLLMInstance.getProviderOptionsJson());

        AzureOpenAiEmbeddingOptions embeddingOptions = AzureOpenAiEmbeddingOptions.builder()
                .deploymentName(resolveEmbeddingModelName(firstNonBlank(
                        optionsParser.stringValue(options, "embeddingDeploymentName"),
                        optionsParser.stringValue(options, "embeddingModel"),
                        optionsParser.stringValue(options, "model"),
                        turLLMInstance.getModelName())))
                .build();

        return new AzureOpenAiEmbeddingModel(
                createClientBuilder(options, turLLMInstance, decryptedApiKey).buildClient(),
                MetadataMode.NONE, embeddingOptions);
    }

    private OpenAIClientBuilder createClientBuilder(Map<String, Object> options,
            TurLLMInstance turLLMInstance, String decryptedApiKey) {
        return new OpenAIClientBuilder()
                .endpoint(requireEndpoint(firstNonBlank(
                        optionsParser.stringValue(options, "endpoint"),
                        turLLMInstance.getUrl()), turLLMInstance))
                .credential(new AzureKeyCredential(requireApiKey(decryptedApiKey, turLLMInstance)));
    }

    private String requireApiKey(String decryptedApiKey, TurLLMInstance turLLMInstance) {
        if (!StringUtils.hasText(decryptedApiKey)) {
            throw new IllegalStateException(
                    "Missing API key for Azure OpenAI provider in LLM instance: " + turLLMInstance.getId());
        }
        return decryptedApiKey;
    }

    private String requireEndpoint(String endpoint, TurLLMInstance turLLMInstance) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException(
                    "Missing endpoint for Azure OpenAI provider in LLM instance: " + turLLMInstance.getId());
        }
        return endpoint;
    }

    private String resolveChatModelName(String configuredModel) {
        return StringUtils.hasText(configuredModel) ? configuredModel : DEFAULT_CHAT_MODEL;
    }

    private String resolveEmbeddingModelName(String configuredModel) {
        return StringUtils.hasText(configuredModel) ? configuredModel : DEFAULT_EMBEDDING_MODEL;
    }

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
