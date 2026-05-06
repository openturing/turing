package com.viglet.turing.system;

import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.domain.llm.TurLLMInstanceDomain;
import com.viglet.turing.domain.llm.TurLLMInstanceRepositoryPort;
import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurLlmSummaryService {

    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLLMInstanceRepositoryPort llmInstanceRepositoryPort;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurLlmCacheService llmCacheService;
    private final TurLLMTokenUsageService tokenUsageService;

    public record SummaryResult(boolean success, String error, String content, boolean canRegenerate) {
    }

    public SummaryResult generate(String cacheKey, String data, String systemPrompt, boolean regenerate) {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(llmId)) {
            return new SummaryResult(false, "No default LLM configured in Global Settings.", null, false);
        }

        TurLLMInstance llmInstance = llmInstanceRepository.findById(llmId).orElse(null);
        if (llmInstance == null || llmInstance.getEnabled() != 1) {
            return new SummaryResult(false, "Default LLM not found or disabled.", null, false);
        }

        boolean canRegenerate = globalSettingsService.isLlmCacheRegenerate();

        if (!regenerate) {
            String cached = llmCacheService.get(cacheKey);
            if (cached != null) {
                return new SummaryResult(true, null, cached, canRegenerate);
            }
        } else {
            llmCacheService.evict(cacheKey);
        }

        try {
            String decryptedApiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
            ChatModel chatModel = llmModelFactory.createChatModel(llmInstance, decryptedApiKey);

            List<Message> messages = List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(data));

            Prompt prompt = new Prompt(messages);
            var response = chatModel.call(prompt);

            tokenUsageService.recordUsage(llmInstance, response, resolveUsername());

            String content = response.getResult().getOutput().getText();

            llmCacheService.put(cacheKey, content);

            return new SummaryResult(true, null, content, canRegenerate);
        } catch (Exception e) {
            log.error("Failed to generate AI summary for key {}", cacheKey, e);
            return new SummaryResult(false, "Failed to generate summary: " + e.getMessage(), null, canRegenerate);
        }
    }

    private String resolveUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    public boolean isAvailable() {
        String llmId = globalSettingsService.getDefaultLlmId();
        if (!StringUtils.hasText(llmId)) {
            return false;
        }
        return llmInstanceRepositoryPort.findById(llmId)
                .map(TurLLMInstanceDomain::isEnabled)
                .orElse(false);
    }
}
