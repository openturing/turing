package com.viglet.turing.genai.provider.store;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import com.viglet.turing.persistence.model.store.TurStoreInstance;

public interface TurGenAiStoreProvider {

    String getPluginType();

    VectorStore createVectorStore(TurStoreInstance turStoreInstance,
            EmbeddingModel embeddingModel,
            String decryptedCredential);
}
