package com.viglet.turing.service.llm.tokenusage;

import java.time.LocalDateTime;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMTokenUsage;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurLLMTokenUsageService {

    private final TurLLMTokenUsageRepository tokenUsageRepository;

    public TurLLMTokenUsageService(TurLLMTokenUsageRepository tokenUsageRepository) {
        this.tokenUsageRepository = tokenUsageRepository;
    }

    public void recordUsage(TurLLMInstance instance, ChatResponse response, String username) {
        try {
            if (response == null || response.getMetadata() == null) {
                return;
            }
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                return;
            }

            long inputTokens = usage.getPromptTokens();
            long outputTokens = usage.getCompletionTokens();
            long totalTokens = usage.getTotalTokens();

            if (totalTokens == 0 && inputTokens == 0 && outputTokens == 0) {
                return;
            }

            TurLLMTokenUsage turLLMTokenUsage = new TurLLMTokenUsage();
            turLLMTokenUsage.setTurLLMInstance(instance);
            turLLMTokenUsage.setVendorId(instance.getTurLLMVendor() != null
                    ? instance.getTurLLMVendor().getId()
                    : "unknown");
            turLLMTokenUsage.setModelName(instance.getModelName());
            turLLMTokenUsage.setUsername(username);
            turLLMTokenUsage.setInputTokens(inputTokens);
            turLLMTokenUsage.setOutputTokens(outputTokens);
            turLLMTokenUsage.setTotalTokens(totalTokens > 0 ? totalTokens : inputTokens + outputTokens);
            turLLMTokenUsage.setCreatedAt(LocalDateTime.now());

            tokenUsageRepository.save(turLLMTokenUsage);

            log.info("[TokenUsage] Recorded: instance={}, model={}, input={}, output={}, total={}, user={}",
                    instance.getId(), instance.getModelName(),
                    inputTokens, outputTokens, turLLMTokenUsage.getTotalTokens(), username);
        } catch (Exception e) {
            log.warn("[TokenUsage] Failed to record token usage: {}", e.getMessage());
        }
    }
}
