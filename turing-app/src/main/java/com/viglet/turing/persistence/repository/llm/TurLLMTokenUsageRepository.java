package com.viglet.turing.persistence.repository.llm;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.llm.TurLLMTokenUsage;

public interface TurLLMTokenUsageRepository extends JpaRepository<TurLLMTokenUsage, String> {

    @Query("""
            SELECT CAST(u.createdAt AS date) AS day,
                   u.turLLMInstance.id AS instanceId,
                   u.turLLMInstance.title AS instanceTitle,
                   u.vendorId AS vendorId,
                   u.modelName AS modelName,
                   SUM(u.inputTokens) AS inputTokens,
                   SUM(u.outputTokens) AS outputTokens,
                   SUM(u.totalTokens) AS totalTokens,
                   COUNT(u) AS requestCount
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end
            GROUP BY CAST(u.createdAt AS date), u.turLLMInstance.id, u.turLLMInstance.title,
                     u.vendorId, u.modelName
            ORDER BY day DESC, totalTokens DESC
            """)
    List<Object[]> findDailyUsage(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT u.turLLMInstance.id AS instanceId,
                   u.turLLMInstance.title AS instanceTitle,
                   u.vendorId AS vendorId,
                   u.modelName AS modelName,
                   SUM(u.inputTokens) AS inputTokens,
                   SUM(u.outputTokens) AS outputTokens,
                   SUM(u.totalTokens) AS totalTokens,
                   COUNT(u) AS requestCount
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end
            GROUP BY u.turLLMInstance.id, u.turLLMInstance.title, u.vendorId, u.modelName
            ORDER BY totalTokens DESC
            """)
    List<Object[]> findMonthlySummary(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * T276 / §XIV.5.2 — platform billing roll-up grouped by tenant. Returns
     * {@code [tenantId, inputTokens, outputTokens, totalTokens, requestCount]}
     * per tenant in a window. Cross-tenant by design (platform-admin / billing).
     */
    @Query("""
            SELECT u.tenantId AS tenantId,
                   SUM(u.inputTokens) AS inputTokens,
                   SUM(u.outputTokens) AS outputTokens,
                   SUM(u.totalTokens) AS totalTokens,
                   COUNT(u) AS requestCount
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end
            GROUP BY u.tenantId
            ORDER BY totalTokens DESC
            """)
    List<Object[]> findUsageByTenant(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    // ---- T290 / §XVI.2 — cost-governance aggregations -------------------
    // Each returns [dimensionKey..., costUsd, totalTokens, requestCount]. Cost
    // is the frozen per-row cost_usd summed; works for every provider (local =
    // 0). All cross-tenant by default — the API restricts the window/scope.

    @Query("""
            SELECT u.agentId AS agentId,
                   SUM(u.costUsd) AS costUsd,
                   SUM(u.totalTokens) AS totalTokens,
                   COUNT(u) AS requestCount
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end
            GROUP BY u.agentId
            ORDER BY costUsd DESC
            """)
    List<Object[]> findCostByAgent(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT u.tenantId AS tenantId,
                   SUM(u.costUsd) AS costUsd,
                   SUM(u.totalTokens) AS totalTokens,
                   COUNT(u) AS requestCount
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end
            GROUP BY u.tenantId
            ORDER BY costUsd DESC
            """)
    List<Object[]> findCostByTenant(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT u.vendorId AS vendorId,
                   u.modelName AS modelName,
                   SUM(u.costUsd) AS costUsd,
                   SUM(u.totalTokens) AS totalTokens,
                   COUNT(u) AS requestCount,
                   SUM(u.inputTokens) AS inputTokens,
                   SUM(u.outputTokens) AS outputTokens
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end
            GROUP BY u.vendorId, u.modelName
            ORDER BY costUsd DESC
            """)
    List<Object[]> findCostByModel(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT u.stage AS stage,
                   SUM(u.costUsd) AS costUsd,
                   SUM(u.totalTokens) AS totalTokens,
                   COUNT(u) AS requestCount
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end
            GROUP BY u.stage
            ORDER BY costUsd DESC
            """)
    List<Object[]> findCostByStage(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("""
            SELECT CAST(u.createdAt AS date) AS day,
                   SUM(u.costUsd) AS costUsd,
                   SUM(u.totalTokens) AS totalTokens,
                   COUNT(u) AS requestCount
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end
            GROUP BY CAST(u.createdAt AS date)
            ORDER BY day ASC
            """)
    List<Object[]> findDailyCostTimeseries(@Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * T291 / §XVI.3 — month-to-date (or any window) spend for one agent, used by
     * the turn-time soft budget gate. Returns {@code 0.0} when the agent has no
     * rows in the window (COALESCE).
     */
    @Query("""
            SELECT COALESCE(SUM(u.costUsd), 0.0)
            FROM TurLLMTokenUsage u
            WHERE u.agentId = :agentId AND u.createdAt >= :start
            """)
    double sumCostByAgentSince(@Param("agentId") String agentId, @Param("start") LocalDateTime start);

    /**
     * T641 / §XXXVII.3 — total LLM spend since {@code start} across all agents.
     * Powers the anonymous-chat hard cost circuit-breaker.
     */
    @Query("""
            SELECT COALESCE(SUM(u.costUsd), 0.0)
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start
            """)
    double sumCostSince(@Param("start") LocalDateTime start);

    /**
     * T742 / §XLIX — cost / tokens / request-count per gateway virtual key in a
     * window, for the per-key spend dashboard (mirrors {@link #findCostByAgent}).
     * Rows with a {@code null} keyId (non-gateway traffic) are excluded.
     */
    @Query("""
            SELECT u.keyId AS keyId,
                   SUM(u.costUsd) AS costUsd,
                   SUM(u.totalTokens) AS totalTokens,
                   COUNT(u) AS requestCount
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end AND u.keyId IS NOT NULL
            GROUP BY u.keyId
            ORDER BY costUsd DESC
            """)
    List<Object[]> findCostByKey(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    /**
     * T742 / §XLIX — month-to-date (or any window) spend for one virtual key, used
     * by the per-key soft budget + hard kill-switch gate. {@code 0.0} when the key
     * has no rows in the window (COALESCE).
     */
    @Query("""
            SELECT COALESCE(SUM(u.costUsd), 0.0)
            FROM TurLLMTokenUsage u
            WHERE u.keyId = :keyId AND u.createdAt >= :start
            """)
    double sumCostByKeySince(@Param("keyId") String keyId, @Param("start") LocalDateTime start);

    /**
     * T786 / §LIII.3 — per-(agent, model) usage roll-up for the right-sizing
     * advisor: the observed spend + input/output token totals + request count each
     * agent ran on each model in the window, so the advisor can derive average
     * prompt sizes and compare the current model's rate against cheaper catalog
     * alternatives. Columns: agentId, vendorId, modelName, SUM(costUsd),
     * SUM(inputTokens), SUM(outputTokens), COUNT.
     */
    @Query("""
            SELECT u.agentId AS agentId,
                   u.vendorId AS vendorId,
                   u.modelName AS modelName,
                   SUM(u.costUsd) AS costUsd,
                   SUM(u.inputTokens) AS inputTokens,
                   SUM(u.outputTokens) AS outputTokens,
                   COUNT(u) AS requestCount
            FROM TurLLMTokenUsage u
            WHERE u.createdAt >= :start AND u.createdAt < :end AND u.agentId IS NOT NULL
            GROUP BY u.agentId, u.vendorId, u.modelName
            ORDER BY costUsd DESC
            """)
    List<Object[]> findUsageByAgentAndModel(@Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
