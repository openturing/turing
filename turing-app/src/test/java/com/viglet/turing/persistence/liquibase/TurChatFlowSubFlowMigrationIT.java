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
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Integration test for the Liquibase changelog applied against a real
 * MySQL engine. H2 is permissive about column types (it silently accepts
 * synonyms that MySQL rejects) so we run the master changelog from scratch
 * on a Testcontainers MySQL image and assert the schema landed exactly as
 * expected for the new sub-flow column.
 *
 * <p>Skipped automatically when Docker isn't available — same pattern as
 * {@link com.viglet.turing.service.storage.TurMinioStorageServiceIT}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Testcontainers(disabledWithoutDocker = true)
class TurChatFlowSubFlowMigrationIT {

    private static final String DB_NAME = "turing_it";
    private static final String DB_USER = "turing";
    private static final String DB_PASSWORD = "turing";
    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD);

    @Test
    void parentStateIdColumnExistsAfterMasterChangelogApplies() throws Exception {
        applyMasterChangelog();

        try (Connection connection = openConnection()) {
            ColumnInfo column = readColumnMetadata(connection, "chat_flow_state", "parentStateId");
            assertThat(column).as("parentStateId column should exist after migration").isNotNull();
            assertThat(column.dataType()).isEqualToIgnoringCase("varchar");
            assertThat(column.maxLength()).isEqualTo(36);
            assertThat(column.nullable()).isTrue();
        }
    }

    @Test
    void preExistingChatFlowStateColumnsRemainIntact() throws Exception {
        applyMasterChangelog();

        try (Connection connection = openConnection()) {
            assertThat(readColumnMetadata(connection, "chat_flow_state", "currentNodeId"))
                    .as("currentNodeId must still exist").isNotNull();
            assertThat(readColumnMetadata(connection, "chat_flow_state", "variablesJson"))
                    .as("variablesJson must still exist").isNotNull();
            assertThat(readColumnMetadata(connection, "chat_flow_state", "conversationId"))
                    .as("conversationId must still exist").isNotNull();
        }
    }

    @Test
    void migrationIsIdempotent_secondApplyIsNoOp() throws Exception {
        applyMasterChangelog();
        // Second apply should not throw — the parentStateId changeset is
        // gated by columnExists with onFail: MARK_RAN, so re-running on
        // an already-migrated database is a no-op.
        applyMasterChangelog();

        try (Connection connection = openConnection()) {
            assertThat(readColumnMetadata(connection, "chat_flow_state", "parentStateId"))
                    .isNotNull();
        }
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private static Connection openConnection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), DB_USER, DB_PASSWORD);
    }

    /**
     * Applies the master changelog using its own connection that Liquibase
     * owns and closes. The metadata-reading tests then open a fresh
     * connection to query the migrated schema.
     */
    private static void applyMasterChangelog() throws Exception {
        Connection connection = openConnection();
        Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
        try (Liquibase liquibase = new Liquibase(CHANGELOG,
                new ClassLoaderResourceAccessor(),
                database)) {
            liquibase.update("");
        }
    }

    private static ColumnInfo readColumnMetadata(Connection connection,
            String table, String column) throws Exception {
        String sql = """
                SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, DB_NAME);
            ps.setString(2, table);
            ps.setString(3, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String dataType = rs.getString("DATA_TYPE");
                long maxLength = rs.getLong("CHARACTER_MAXIMUM_LENGTH");
                boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                return new ColumnInfo(dataType, maxLength, nullable);
            }
        }
    }

    private record ColumnInfo(String dataType, long maxLength, boolean nullable) {
    }
}
