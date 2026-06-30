/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.llm;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.llm.TurAiUsageDaily;

/**
 * T183 / §X.14.c — vendor-reported daily usage/cost rows. The importer
 * re-imports a date window idempotently by deleting the existing rows for that
 * vendor+window first ({@link #deleteByVendorIdAndUsageDateBetween}), so a
 * re-run never double-counts.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurAiUsageDailyRepository extends JpaRepository<TurAiUsageDaily, String> {

    @Modifying
    @Query("DELETE FROM TurAiUsageDaily u WHERE u.vendorId = :vendorId "
            + "AND u.usageDate >= :from AND u.usageDate <= :to")
    void deleteByVendorIdAndUsageDateBetween(@Param("vendorId") String vendorId,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * T184 — vendor-reported spend per (vendor, model) over a day window.
     * Returns {@code [vendorId, modelName, costUsd, inputTokens, outputTokens]}.
     */
    @Query("""
            SELECT u.vendorId AS vendorId,
                   u.modelName AS modelName,
                   SUM(u.costUsd) AS costUsd,
                   SUM(u.inputTokens) AS inputTokens,
                   SUM(u.outputTokens) AS outputTokens
            FROM TurAiUsageDaily u
            WHERE u.usageDate >= :from AND u.usageDate <= :to
            GROUP BY u.vendorId, u.modelName
            ORDER BY costUsd DESC
            """)
    List<Object[]> findVendorCostByModel(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** T184 — vendor-reported total USD spend over a day window ({@code 0.0} when empty). */
    @Query("""
            SELECT COALESCE(SUM(u.costUsd), 0.0)
            FROM TurAiUsageDaily u
            WHERE u.usageDate >= :from AND u.usageDate <= :to
            """)
    double sumCostBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
