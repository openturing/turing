package com.viglet.turing.system;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@Service
public class TurGlobalSettingsService {
    private final TurConfigVarRepository turConfigVarRepository;

    public TurGlobalSettingsService(TurConfigVarRepository turConfigVarRepository) {
        this.turConfigVarRepository = turConfigVarRepository;
    }

    public TurGlobalDecimalSeparator getDecimalSeparator() {
        String value = turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR)
                .map(TurConfigVar::getValue)
                .orElse(TurConfigVarOnStartup.DECIMAL_SEPARATOR_DEFAULT_VALUE);

        try {
            return TurGlobalDecimalSeparator
                    .valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return TurGlobalDecimalSeparator
                    .valueOf(TurConfigVarOnStartup.DECIMAL_SEPARATOR_DEFAULT_VALUE);
        }
    }

    public TurGlobalDecimalSeparator updateDecimalSeparator(TurGlobalDecimalSeparator decimalSeparator) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DECIMAL_SEPARATOR);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(decimalSeparator.name());
        turConfigVarRepository.save(configVar);
        return decimalSeparator;
    }

    public String getPythonExecutable() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updatePythonExecutable(String pythonExecutable) {
        String value = pythonExecutable == null ? "" : pythonExecutable.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.PYTHON_EXECUTABLE);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public String getDefaultLlmId() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_LLM)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateDefaultLlmId(String defaultLlmId) {
        String value = defaultLlmId == null ? "" : defaultLlmId.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_LLM)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DEFAULT_LLM);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public TurEmailProviderType getEmailProvider() {
        String value = turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_PROVIDER)
                .map(TurConfigVar::getValue)
                .orElse(TurConfigVarOnStartup.EMAIL_PROVIDER_DEFAULT_VALUE);
        try {
            return TurEmailProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return TurEmailProviderType.valueOf(TurConfigVarOnStartup.EMAIL_PROVIDER_DEFAULT_VALUE);
        }
    }

    public TurEmailProviderType updateEmailProvider(TurEmailProviderType emailProvider) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_PROVIDER)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.EMAIL_PROVIDER);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(emailProvider.name());
        turConfigVarRepository.save(configVar);
        return emailProvider;
    }

    public String getEmailApiKey() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_API_KEY)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateEmailApiKey(String emailApiKey) {
        String value = emailApiKey == null ? "" : emailApiKey.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.EMAIL_API_KEY)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.EMAIL_API_KEY);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public String getSenderEmail() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_EMAIL)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateSenderEmail(String senderEmail) {
        String value = senderEmail == null ? "" : senderEmail.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_EMAIL)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.SENDER_EMAIL);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public String getSenderName() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_NAME)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateSenderName(String senderName) {
        String value = senderName == null ? "" : senderName.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.SENDER_NAME)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.SENDER_NAME);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public boolean isLlmCacheEnabled() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED)
                .map(TurConfigVar::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean updateLlmCacheEnabled(boolean enabled) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_ENABLED)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.LLM_CACHE_ENABLED);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(enabled));
        turConfigVarRepository.save(configVar);
        return enabled;
    }

    public long getLlmCacheTtlMs() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS)
                .map(TurConfigVar::getValue)
                .map(v -> {
                    try {
                        return Long.parseLong(v.trim());
                    } catch (NumberFormatException e) {
                        return 3_600_000L;
                    }
                })
                .orElse(3_600_000L);
    }

    public long updateLlmCacheTtlMs(long ttlMs) {
        long value = Math.max(0, ttlMs);
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_TTL_MS)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.LLM_CACHE_TTL_MS);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(value));
        turConfigVarRepository.save(configVar);
        return value;
    }

    public boolean isLlmCacheRegenerate() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_REGENERATE)
                .map(TurConfigVar::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean updateLlmCacheRegenerate(boolean regenerate) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.LLM_CACHE_REGENERATE)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.LLM_CACHE_REGENERATE);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(regenerate));
        turConfigVarRepository.save(configVar);
        return regenerate;
    }

    public String getRecipientEmail() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RECIPIENT_EMAIL)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateRecipientEmail(String recipientEmail) {
        String value = recipientEmail == null ? "" : recipientEmail.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.RECIPIENT_EMAIL)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RECIPIENT_EMAIL);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public boolean isRagEnabled() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_ENABLED)
                .map(TurConfigVar::getValue)
                .map(Boolean::parseBoolean)
                .orElse(false);
    }

    public boolean updateRagEnabled(boolean enabled) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.RAG_ENABLED)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.RAG_ENABLED);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(String.valueOf(enabled));
        turConfigVarRepository.save(configVar);
        return enabled;
    }

    public String getDefaultEmbeddingModelId() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateDefaultEmbeddingModelId(String id) {
        String value = id == null ? "" : id.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DEFAULT_EMBEDDING_MODEL_ID);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }

    public String getDefaultEmbeddingStoreId() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updateDefaultEmbeddingStoreId(String id) {
        String value = id == null ? "" : id.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DEFAULT_EMBEDDING_STORE_ID);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }
}
