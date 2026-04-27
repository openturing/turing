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

package com.viglet.turing.exchange.sn;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;

import lombok.extern.slf4j.Slf4j;

/**
 * Validates the exported SN Site exchange data to detect missing fields
 * or objects that would cause import failures.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Slf4j
@Component
public class TurSNSiteExportValidator {

    public record ValidationResult(List<String> errors, List<String> warnings) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }

        public boolean isValid() {
            return errors.isEmpty();
        }
    }

    public ValidationResult validate(TurExchange turExchange) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (turExchange == null) {
            errors.add("Exchange data is null.");
            return new ValidationResult(errors, warnings);
        }

        if (turExchange.getSnSites() == null || turExchange.getSnSites().isEmpty()) {
            errors.add("No SN Sites found in export.");
            return new ValidationResult(errors, warnings);
        }

        for (TurSNSiteExchange site : turExchange.getSnSites()) {
            validateSite(site, turExchange, errors, warnings);
        }

        if (!errors.isEmpty() || !warnings.isEmpty()) {
            log.info("Export validation: {} error(s), {} warning(s)", errors.size(), warnings.size());
            errors.forEach(e -> log.error("  ERROR: {}", e));
            warnings.forEach(w -> log.warn("  WARNING: {}", w));
        }

        return new ValidationResult(errors, warnings);
    }

    private void validateSite(TurSNSiteExchange site, TurExchange turExchange,
            List<String> errors, List<String> warnings) {
        String siteLabel = site.getName() != null ? site.getName() : site.getId();

        if (site.getId() == null || site.getId().isBlank()) {
            errors.add("SN Site is missing 'id'.");
            return;
        }
        if (site.getName() == null || site.getName().isBlank()) {
            errors.add("SN Site '%s' is missing 'name'.".formatted(site.getId()));
        }

        validateSEInstance(site, turExchange, siteLabel, errors, warnings);
        validateLocales(site, siteLabel, errors);
        validateFieldExts(site, siteLabel, warnings);
        validateGenAi(site, turExchange, siteLabel, warnings);
        validateCustomSorts(site, siteLabel, warnings);
        validateOptionalFields(site, siteLabel, warnings);
    }

    private void validateSEInstance(TurSNSiteExchange site, TurExchange turExchange,
            String siteLabel, List<String> errors, List<String> warnings) {
        if (site.getTurSEInstance() == null || site.getTurSEInstance().isBlank()) {
            errors.add("SN Site '%s': missing SE Instance reference.".formatted(siteLabel));
        } else if (turExchange.getSe() != null) {
            boolean found = turExchange.getSe().stream()
                    .anyMatch(se -> site.getTurSEInstance().equals(se.getId()));
            if (!found) {
                warnings.add("SN Site '%s': SE Instance '%s' not found in export. Import will try to resolve or create it."
                        .formatted(siteLabel, site.getTurSEInstance()));
            }
        }
    }

    private void validateLocales(TurSNSiteExchange site, String siteLabel, List<String> errors) {
        if (site.getTurSNSiteLocales() == null || site.getTurSNSiteLocales().isEmpty()) {
            errors.add("SN Site '%s': no locales defined. At least one locale is required."
                    .formatted(siteLabel));
        }
    }

    private void validateFieldExts(TurSNSiteExchange site, String siteLabel, List<String> warnings) {
        if (site.getTurSNSiteFieldExts() == null || site.getTurSNSiteFieldExts().isEmpty()) {
            warnings.add("SN Site '%s': no field extensions defined.".formatted(siteLabel));
            return;
        }
        for (TurSNSiteFieldExt fieldExt : site.getTurSNSiteFieldExts()) {
            if (fieldExt.getName() == null || fieldExt.getName().isBlank()) {
                warnings.add("SN Site '%s': field extension '%s' has no name."
                        .formatted(siteLabel, fieldExt.getId()));
            }
            if (fieldExt.getSnType() == null) {
                warnings.add("SN Site '%s': field '%s' has no snType."
                        .formatted(siteLabel, fieldExt.getName()));
            }
            if (fieldExt.getType() == null) {
                warnings.add("SN Site '%s': field '%s' has no SE type."
                        .formatted(siteLabel, fieldExt.getName()));
            }
        }
    }

    private void validateGenAi(TurSNSiteExchange site, TurExchange turExchange,
            String siteLabel, List<String> warnings) {
        TurSNSiteGenAiExchange genAi = site.getTurSNSiteGenAi();
        if (genAi == null) {
            return;
        }
        // Since 2026.2.4 the site GenAI binding only points to an AI agent.
        // System prompt now lives on the agent (out of scope for SN export
        // validation). Agents are not bundled into the SN export, so this
        // method only checks the binding has an agent id.
        if (genAi.getTurAIAgent() == null || genAi.getTurAIAgent().isBlank()) {
            warnings.add("SN Site '%s': GenAI binding has no AI Agent reference."
                    .formatted(siteLabel));
        }
        // Reference turExchange to keep the parameter purposeful for future use
        // (e.g., once agents are bundled into the export).
        if (turExchange == null) {
            warnings.add("SN Site '%s': missing exchange context for GenAI validation."
                    .formatted(siteLabel));
        }
    }

    private void validateCustomSorts(TurSNSiteExchange site, String siteLabel, List<String> warnings) {
        if (site.getTurSNSiteCustomSorts() == null) {
            return;
        }
        for (TurSNSiteCustomSort customSort : site.getTurSNSiteCustomSorts()) {
            if (customSort.getName() == null || customSort.getName().isBlank()) {
                warnings.add("SN Site '%s': custom sort '%s' has no name."
                        .formatted(siteLabel, customSort.getId()));
            }
            if (customSort.getItems() == null || customSort.getItems().isEmpty()) {
                warnings.add("SN Site '%s': custom sort '%s' has no sort items."
                        .formatted(siteLabel, customSort.getName()));
            }
        }
    }

    private void validateOptionalFields(TurSNSiteExchange site, String siteLabel, List<String> warnings) {
        if (site.getDefaultTitleField() == null || site.getDefaultTitleField().isBlank()) {
            warnings.add("SN Site '%s': missing default title field.".formatted(siteLabel));
        }
        if (site.getDefaultURLField() == null || site.getDefaultURLField().isBlank()) {
            warnings.add("SN Site '%s': missing default URL field.".formatted(siteLabel));
        }
    }
}
