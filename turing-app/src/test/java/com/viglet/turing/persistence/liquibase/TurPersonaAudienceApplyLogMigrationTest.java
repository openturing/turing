/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * T831 / §LXI.7 — applies the whole master changelog to a throwaway in-memory
 * H2 and asserts the audience-apply provenance table landed.
 *
 * <p>The sibling {@code TurChatFlowSubFlowMigrationIT} runs against a real MySQL
 * because H2 is permissive about column types. This one is deliberately the
 * cheaper, Docker-free complement: it cannot catch a MySQL-only type rejection,
 * but it does catch what a new changeset most often gets wrong and what nothing
 * else here would notice until a deploy — a changelog that does not parse, one
 * that is not reachable from the master include list, a duplicate changeset id,
 * or a table that simply never gets created. It runs in the ordinary unit suite,
 * which is where a schema mistake is cheapest to find.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPersonaAudienceApplyLogMigrationTest {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final String TABLE = "ai_persona_audience_apply_log";

    /**
     * A fresh, private database per run — no reuse, so nothing is pre-migrated.
     * The identifier flags mirror {@code application.yaml}'s H2 URL exactly:
     * changeset preconditions resolve table names through the same casing rules
     * the DDL does, and a mismatch there makes an idempotency gate silently
     * disagree with the statement it guards.
     */
    private static String freshUrl() {
        return "jdbc:h2:mem:changelog_" + System.nanoTime()
                + ";DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false"
                + ";CASE_INSENSITIVE_IDENTIFIERS=true";
    }

    @Test
    void masterChangelogAppliesAndCreatesTheAudienceApplyLog() throws Exception {
        String url = freshUrl();
        applyMasterChangelog(url);

        // A second connection: closing Liquibase closes the one it migrated on.
        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            Map<String, ColumnInfo> columns = readColumns(connection, TABLE);
            assertThat(columns)
                    .as("the T831 provenance table must exist after the master changelog")
                    .isNotEmpty();

            // The identity of the row, and the pair that makes drift attributable.
            assertThat(columns).containsKeys("id", "tenantid", "persona_id", "project_id",
                    "field", "old_value", "new_value", "rationale", "llm_used", "applied_at",
                    "applied_by");

            assertThat(columns.get("persona_id").nullable())
                    .as("a row without a persona explains nothing").isFalse();
            assertThat(columns.get("field").nullable())
                    .as("a row without a field explains nothing").isFalse();
            assertThat(columns.get("applied_at").nullable())
                    .as("a row without a time cannot be ordered").isFalse();
            assertThat(columns.get("project_id").nullable())
                    .as("nullable on purpose: the row outlives the project that suggested it")
                    .isTrue();
        }
    }

    @Test
    void reRunningTheChangelogIsANoOp() throws Exception {
        // The whole idempotency convention: the app runs between changeset
        // authoring, so every changeset carries a precondition + MARK_RAN.
        String url = freshUrl();
        applyMasterChangelog(url);
        applyMasterChangelog(url);

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            assertThat(readColumns(connection, TABLE)).isNotEmpty();
        }
    }

    private void applyMasterChangelog(String url) throws Exception {
        Connection connection = DriverManager.getConnection(url, "sa", "");
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        try (Liquibase liquibase = new Liquibase(CHANGELOG,
                new ClassLoaderResourceAccessor(), database)) {
            liquibase.update("");
        }
    }

    private Map<String, ColumnInfo> readColumns(Connection connection, String table)
            throws Exception {
        Map<String, ColumnInfo> columns = new HashMap<>();
        try (ResultSet rs = connection.getMetaData()
                .getColumns(null, null, table.toUpperCase(), null)) {
            collect(rs, columns);
        }
        if (columns.isEmpty()) {
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, null)) {
                collect(rs, columns);
            }
        }
        return columns;
    }

    private void collect(ResultSet rs, Map<String, ColumnInfo> into) throws Exception {
        while (rs.next()) {
            into.put(rs.getString("COLUMN_NAME").toLowerCase(),
                    new ColumnInfo(rs.getString("TYPE_NAME"),
                            rs.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable));
        }
    }

    private record ColumnInfo(String dataType, boolean nullable) {
    }
}
