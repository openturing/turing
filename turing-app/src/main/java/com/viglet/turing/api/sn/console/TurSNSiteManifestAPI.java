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

package com.viglet.turing.api.sn.console;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.sn.manifest.TurSNManifestDiff;
import com.viglet.turing.sn.manifest.TurSNManifestMigrationRequiredException;
import com.viglet.turing.sn.manifest.TurSNManifestResult;
import com.viglet.turing.sn.manifest.TurSNSiteManifest;
import com.viglet.turing.sn.manifest.TurSNSiteManifestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Field-manifest site provisioning endpoint (T382 / §XX.2).
 *
 * <p>{@code POST /api/sn/manifest} takes a single declarative
 * {@link TurSNSiteManifest} (site + locales + ordered field specs) and converges
 * the SN site and its field schema to it in one idempotent call — re-posting the
 * same manifest is a no-op. This is how a structured connector (Dumont, cursarei)
 * stands up its schema as code instead of hand-clicking the console.</p>
 *
 * <p>Schema-as-code (T386 / §XX.6): {@code POST /api/sn/manifest/plan} returns the
 * diff a manifest would apply (the dry-run), and provisioning rejects an
 * undeclared breaking change (a {@code type}/{@code multiValued} rewrite of an
 * existing field) with HTTP 409 — additive changes converge silently, breaking
 * changes must be declared via a migration.</p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@RestController
@RequestMapping("/api/sn/manifest")
@Tag(name = "Semantic Navigation Site Manifest",
        description = "Declarative, idempotent SN site + field-schema provisioning")
@RequiredArgsConstructor
public class TurSNSiteManifestAPI {

    private final TurSNSiteManifestService manifestService;

    @Operation(summary = "Provision (create or converge) an SN site and its field schema from a manifest")
    @PostMapping
    @Secured({"ROLE_ADMIN", "SN_CREATE"})
    public ResponseEntity<Object> provision(@RequestBody TurSNSiteManifest manifest) {
        try {
            TurSNManifestResult result = manifestService.provision(manifest);
            return ResponseEntity.ok(result);
        } catch (TurSNManifestMigrationRequiredException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", e.getMessage(), "breakingChanges", e.getChanges()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Compute the diff a manifest would apply against the live site (dry-run, no mutation)")
    @PostMapping("/plan")
    @Secured({"ROLE_ADMIN", "SN_CREATE"})
    public ResponseEntity<Object> plan(@RequestBody TurSNSiteManifest manifest) {
        try {
            TurSNManifestDiff diff = manifestService.plan(manifest);
            return ResponseEntity.ok(diff);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
