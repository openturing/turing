package com.viglet.turing.genai.provider.llm;

import java.util.OptionalInt;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;

public interface TurGenAiLlmProvider {

    String getPluginType();

    ChatModel createChatModel(TurLLMInstance turLLMInstance, String decryptedApiKey);

    EmbeddingModel createEmbeddingModel(TurLLMInstance turLLMInstance, String decryptedApiKey);

    /**
     * Fetches the actual context window size from the provider's API.
     * Returns empty if the provider doesn't support runtime discovery,
     * in which case the stored contextWindow value from the instance is used.
     */
    default OptionalInt fetchContextWindow(TurLLMInstance turLLMInstance, String decryptedApiKey) {
        return OptionalInt.empty();
    }
}
