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
package com.viglet.turing.onstartup;

import com.viglet.turing.exchange.TurImportExchange;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * On first install, imports all ZIP files found in the {@code export/} directory
 * of the working directory. This allows pre-seeding a Turing instance with
 * SN sites, content, and SPA templates by simply placing export ZIPs in the
 * {@code export/} folder before the first launch.
 * <p>
 * Runs after {@link TurOnStartup} (which creates default data) via {@code @Order(2)}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Component
@Order(2)
public class TurExportImportOnStartup implements ApplicationRunner {

    private static final String EXPORT_DIR = "export";
    private static final String AUTO_IMPORT_DONE = "EXPORT_AUTO_IMPORT";

    private final TurConfigVarRepository turConfigVarRepository;
    private final TurImportExchange turImportExchange;

    public TurExportImportOnStartup(TurConfigVarRepository turConfigVarRepository,
                                    TurImportExchange turImportExchange) {
        this.turConfigVarRepository = turConfigVarRepository;
        this.turImportExchange = turImportExchange;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (turConfigVarRepository.findById(AUTO_IMPORT_DONE).isPresent()) {
            return;
        }

        File exportDir = findExportDir();

        if (exportDir == null) {
            return;
        }

        File[] zipFiles = exportDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".zip"));
        if (zipFiles == null || zipFiles.length == 0) {
            log.info("No ZIP files found in '{}', skipping auto-import.", exportDir.getAbsolutePath());
            return;
        }

        Arrays.sort(zipFiles);

        log.info("Found {} ZIP file(s) in '{}'. Starting auto-import...", zipFiles.length, exportDir.getAbsolutePath());

        for (File zipFile : zipFiles) {
            importZipFile(zipFile);
        }

        log.info("Auto-import from '{}' completed.", EXPORT_DIR);

        // Mark as done so it doesn't run again
        TurConfigVar marker = new TurConfigVar();
        marker.setId(AUTO_IMPORT_DONE);
        marker.setPath("/system");
        marker.setValue("true");
        turConfigVarRepository.save(marker);
    }

    private File findExportDir() {
        String userDir = System.getProperty("user.dir");
        log.info("Looking for '{}/' directory (user.dir='{}')", EXPORT_DIR, userDir);

        // Check: {user.dir}/export/
        File candidate = Paths.get(userDir, EXPORT_DIR).toFile();
        if (candidate.isDirectory()) {
            log.info("Found export directory at '{}'", candidate.getAbsolutePath());
            return candidate;
        }

        // Check: {user.dir}/../export/ (for when running from turing-app/)
        candidate = Paths.get(userDir, "..", EXPORT_DIR).normalize().toFile();
        if (candidate.isDirectory()) {
            log.info("Found export directory at '{}'", candidate.getAbsolutePath());
            return candidate;
        }

        log.info("No '{}/' directory found. Checked: '{}' and parent. Skipping auto-import.",
                EXPORT_DIR, userDir);
        return null;
    }

    private void importZipFile(File zipFile) {
        log.info("Auto-importing '{}'...", zipFile.getName());
        try (FileInputStream input = new FileInputStream(zipFile)) {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    zipFile.getName(), zipFile.getName(),
                    "application/zip", IOUtils.toByteArray(input));
            turImportExchange.importFromMultipartFile(multipartFile, true, true, null);
            log.info("Successfully auto-imported '{}'.", zipFile.getName());
        } catch (IOException e) {
            log.error("Failed to auto-import '{}': {}", zipFile.getName(), e.getMessage(), e);
        }
    }
}
