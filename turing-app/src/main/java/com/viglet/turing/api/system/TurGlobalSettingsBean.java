package com.viglet.turing.api.system;

import com.viglet.turing.system.TurEmailProviderType;
import com.viglet.turing.system.TurGlobalDecimalSeparator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurGlobalSettingsBean {
    private TurGlobalDecimalSeparator decimalSeparator;
    private String pythonExecutable;
    private String defaultLlmId;
    private boolean llmCacheEnabled;
    private long llmCacheTtlMs;
    private boolean llmCacheRegenerate;
    private TurEmailProviderType emailProvider;
    private String emailApiKey;
    private String senderEmail;
    private String senderName;
    private String recipientEmail;
    private boolean ragEnabled;
    private String defaultEmbeddingModelId;
    private String defaultEmbeddingStoreId;
}
