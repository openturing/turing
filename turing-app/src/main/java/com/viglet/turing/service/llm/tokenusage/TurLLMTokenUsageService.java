package com.viglet.turing.service.llm.tokenusage;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import com.viglet.turing.observability.TurLangSmithExporter;
import com.viglet.turing.observability.TurLlmObservation;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.llm.TurLLMTokenUsage;
import com.viglet.turing.persistence.repository.llm.TurLLMTokenUsageRepository;
import com.viglet.turing.service.llm.price.TurLLMPriceService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurLLMTokenUsageService {

    /** T289 — stage tag when the caller doesn't supply one (legacy / direct API). */
    public static final String STAGE_OTHER = "chat.other";

    private final TurLLMTokenUsageRepository tokenUsageRepository;
    private final TurLlmObservation observation;
    private final com.viglet.turing.tenant.TurTenantContext tenantContext;
    private final TurLLMPriceService priceService;
    private final TurLangSmithExporter langSmithExporter;

    public TurLLMTokenUsageService(TurLLMTokenUsageRepository tokenUsageRepository,
            TurLlmObservation observation,
            com.viglet.turing.tenant.TurTenantContext tenantContext,
            TurLLMPriceService priceService,
            TurLangSmithExporter langSmithExporter) {
        this.tokenUsageRepository = tokenUsageRepository;
        this.observation = observation;
        this.tenantContext = tenantContext;
        this.priceService = priceService;
        this.langSmithExporter = langSmithExporter;
    }

    /**
     * Backward-compatible overload: records usage with no agent attribution and
     * the {@link #STAGE_OTHER} stage. Existing callers (direct LLM chat API,
     * weekly report, …) keep compiling unchanged; the agent chat executor uses
     * {@link #recordUsage(TurLLMInstance, ChatResponse, String, String, String)}
     * to attribute spend to an agent and stage.
     */
    public void recordUsage(TurLLMInstance instance, ChatResponse response, String username) {
        recordUsage(instance, response, username, null, STAGE_OTHER);
    }

    /**
     * T289 / §XVI.1 — record per-turn token usage AND the computed USD cost,
     * tagged with the originating agent and cost stage so the Block L dashboard
     * can slice spend per agent / tenant / model / stage.
     *
     * @param agentId originating agent id, or {@code null} for non-agent calls
     * @param stage   coarse cost stage (e.g. {@code chat.live}); falls back to
     *                {@link #STAGE_OTHER} when blank
     * @return the frozen USD cost recorded for this turn, or {@code 0.0} when
     *         nothing was recorded (no usage metadata) — lets the caller feed
     *         the T291 per-turn soft-cap check without a second computation.
     */
    public double recordUsage(TurLLMInstance instance, ChatResponse response, String username,
            String agentId, String stage) {
        return recordUsage(instance, response, username, agentId, stage, null);
    }

    /**
     * T742 / §XLIX — as above, additionally attributing the turn to a Governed
     * LLM Gateway virtual key ({@code keyId}, {@code null} for non-gateway calls)
     * so per-key spend can be aggregated and budget-gated.
     */
    public double recordUsage(TurLLMInstance instance, ChatResponse response, String username,
            String agentId, String stage, String keyId) {
        try {
            if (response == null || response.getMetadata() == null) {
                return 0.0;
            }
            Usage usage = response.getMetadata().getUsage();
            if (usage == null) {
                return 0.0;
            }

            long inputTokens = usage.getPromptTokens();
            long outputTokens = usage.getCompletionTokens();
            long totalTokens = usage.getTotalTokens();

            if (totalTokens == 0 && inputTokens == 0 && outputTokens == 0) {
                return 0.0;
            }

            String vendorId = instance.getTurLLMVendor() != null
                    ? instance.getTurLLMVendor().getId()
                    : "unknown";
            double costUsd = priceService.computeCost(
                    vendorId, instance.getModelName(), inputTokens, outputTokens);

            TurLLMTokenUsage turLLMTokenUsage = new TurLLMTokenUsage();
            turLLMTokenUsage.setTurLLMInstance(instance);
            turLLMTokenUsage.setVendorId(vendorId);
            turLLMTokenUsage.setModelName(instance.getModelName());
            turLLMTokenUsage.setUsername(username);
            turLLMTokenUsage.setInputTokens(inputTokens);
            turLLMTokenUsage.setOutputTokens(outputTokens);
            turLLMTokenUsage.setTotalTokens(totalTokens > 0 ? totalTokens : inputTokens + outputTokens);
            turLLMTokenUsage.setCreatedAt(LocalDateTime.now(ZoneId.systemDefault()));
            // T276 / §XIV.5.2 — attribute the cost to the current tenant.
            turLLMTokenUsage.setTenantId(tenantContext.resolveCurrentTenant());
            // T289 / §XVI.1 — agent + stage attribution and frozen USD cost.
            turLLMTokenUsage.setAgentId(agentId);
            turLLMTokenUsage.setStage(stage == null || stage.isBlank() ? STAGE_OTHER : stage);
            turLLMTokenUsage.setCostUsd(costUsd);
            // T742 / §XLIX — per-key attribution for the Governed LLM Gateway.
            turLLMTokenUsage.setKeyId(keyId);

            tokenUsageRepository.save(turLLMTokenUsage);

            // Mirror persisted usage into Micrometer counters for live dashboards.
            String providerTag = instance.getTurLLMVendor() != null
                    ? instance.getTurLLMVendor().getId()
                    : "unknown";
            observation.recordTokens(providerTag, instance.getModelName(), inputTokens, outputTokens);

            // T128 / §IX.9.a — opt-in, fail-open mirror of the turn to LangSmith.
            String resolvedStage = stage == null || stage.isBlank() ? STAGE_OTHER : stage;
            langSmithExporter.exportTurn(vendorId, instance.getModelName(), agentId, resolvedStage,
                    username, inputTokens, outputTokens, costUsd);

            log.info("[TokenUsage] Recorded: instance={}, model={}, input={}, output={}, total={}, cost=${}, user={}",
                    instance.getId(), instance.getModelName(),
                    inputTokens, outputTokens, turLLMTokenUsage.getTotalTokens(), costUsd, username);
            return costUsd;
        } catch (Exception e) {
            log.warn("[TokenUsage] Failed to record token usage: {}", e.getMessage());
            return 0.0;
        }
    }
}
