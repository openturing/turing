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
}
