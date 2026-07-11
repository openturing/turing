/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.api.sn.migration;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.sn.migration.TurAlgoliaMigrationRequest;
import com.viglet.turing.sn.migration.TurAlgoliaMigrationService;
import com.viglet.turing.sn.migration.TurElasticsearchMigrationService;
import com.viglet.turing.sn.migration.TurEsMigrationRequest;
import com.viglet.turing.sn.migration.TurMigrationCompareRequest;
import com.viglet.turing.sn.migration.TurMigrationCompareResult;
import com.viglet.turing.sn.migration.TurMigrationCompareService;
import com.viglet.turing.sn.migration.TurMigrationException;
import com.viglet.turing.sn.migration.TurMigrationResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Source-engine migration endpoints (Block AO / §XXXVIII).
 *
 * <p>Turns "switch from Algolia / Elasticsearch to Turing" from a documented
 * manual path into one API call: point Turing at a source index (URL + credentials
 * + index name), and it derives the field manifest, provisions the SN site, and
 * imports every record through the existing indexing queue. {@code dryRun=true}
 * returns the derived manifest without provisioning or importing, so the schema
 * can be reviewed first. This is the endpoint the {@code turing migrate} CLI
 * (T659) wraps.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/migrate")
@Tag(name = "Semantic Navigation Migration",
        description = "Import an Algolia / Elasticsearch index into an SN site")
@RequiredArgsConstructor
public class TurSNMigrationAPI {

    private static final String ERROR = "error";

    private final TurElasticsearchMigrationService elasticsearchMigrationService;
    private final TurAlgoliaMigrationService algoliaMigrationService;
    private final TurMigrationCompareService migrationCompareService;

    @Operation(summary = "Migrate one Elasticsearch index into an SN site (mapping -> manifest, docs -> import)")
    @PostMapping("/elasticsearch")
    @Secured({"ROLE_ADMIN", "SN_CREATE"})
    public ResponseEntity<Object> elasticsearch(@RequestBody TurEsMigrationRequest request) {
        try {
            TurMigrationResult result = elasticsearchMigrationService.migrate(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(ERROR, e.getMessage()));
        } catch (TurMigrationException e) {
            log.warn("[T657] Elasticsearch migration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(ERROR, e.getMessage()));
        }
    }

    @Operation(summary = "Migrate one Algolia index into an SN site (settings + sample -> manifest, records -> import)")
    @PostMapping("/algolia")
    @Secured({"ROLE_ADMIN", "SN_CREATE"})
    public ResponseEntity<Object> algolia(@RequestBody TurAlgoliaMigrationRequest request) {
        try {
            TurMigrationResult result = algoliaMigrationService.migrate(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(ERROR, e.getMessage()));
        } catch (TurMigrationException e) {
            log.warn("[T658] Algolia migration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(ERROR, e.getMessage()));
        }
    }

    @Operation(summary = "Shadow-compare a query set against the source engine and the migrated SN site")
    @PostMapping("/compare")
    @Secured({"ROLE_ADMIN", "SN_CREATE"})
    public ResponseEntity<Object> compare(@RequestBody TurMigrationCompareRequest request) {
        try {
            TurMigrationCompareResult result = migrationCompareService.compare(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(ERROR, e.getMessage()));
        } catch (TurMigrationException e) {
            log.warn("[T661] Migration comparison failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(ERROR, e.getMessage()));
        }
    }
}
