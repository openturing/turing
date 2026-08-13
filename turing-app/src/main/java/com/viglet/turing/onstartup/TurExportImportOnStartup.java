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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * On first install, imports all ZIP files found in the {@code export/} directory
 * of the working directory. This allows pre-seeding a Turing instance with
 * SN sites, content, and SPA templates by simply placing export ZIPs in the
 * {@code export/} folder before the first launch.
 * <p>
 * Runs after {@link TurOnStartup} (which creates default data) via {@code @Order(2)}.
 *
 * <h2>What "done" means (T949 / §XIV.8.8)</h2>
 *
 * <p>The one-shot guard used to be written whether or not anything landed, so a
 * boot that imported nothing was indistinguishable from one that imported
 * everything — and since the guard is what stops the next boot from trying, a
 * corrupt ZIP disabled seeding for that database permanently. Recovering meant
 * knowing to delete a row from {@code config_var}. T373 made that load-bearing:
 * the seeded GLOBAL defaults are the only search engine and vector store a fresh
 * Cloud deployment has.
 *
 * <p>Two markers now, because a failed file and a finished directory are
 * different facts:
 * <ul>
 *   <li>{@link #AUTO_IMPORT_DONE} — the directory is complete. Written only when
 *       every ZIP succeeded, and short-circuits every later boot exactly as
 *       before, so an existing installation and a ZIP dropped in after
 *       completion both behave unchanged.</li>
 *   <li>{@code EXPORT_AUTO_IMPORT_<hash>} — one per ZIP that imported cleanly.
 *       On the boot after a partial failure these are what stop the successes
 *       being imported <em>again</em>, which matters because a re-import is
 *       destructive: {@code TurSNSiteImport} deletes and recreates a site that
 *       already exists, taking any change made to it since with it. Only the
 *       ZIPs that failed are retried.</li>
 * </ul>
 *
 * <p>A failure is also no longer only an exception. {@code importFromMultipartFile}
 * reports a corrupt or malformed archive by <em>returning</em>
 * {@code ImportResult.error(...)}, which this runner used to ignore while logging
 * success.
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
    /**
     * Per-file marker prefix. The id column is 50 chars, and a ZIP's name is not
     * bounded, so the key carries a digest and the name rides in {@code value}
     * where it stays readable to whoever is diagnosing a stuck seed.
     */
    private static final String FILE_MARKER_PREFIX = "EXPORT_AUTO_IMPORT_";
    private static final String MARKER_PATH = "/system";

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

        log.info("Found {} ZIP file(s) in '{}'. Starting auto-import...", zipFiles.length,
                exportDir.getAbsolutePath());

        int failed = 0;
        for (File zipFile : zipFiles) {
            if (alreadyImported(zipFile)) {
                log.info("Skipping '{}' — already imported by an earlier boot.", zipFile.getName());
                continue;
            }
            if (importZipFile(zipFile)) {
                saveMarker(fileMarkerId(zipFile), zipFile.getName());
            } else {
                failed++;
            }
        }

        if (failed > 0) {
            // Deliberately NOT writing the directory marker: it is what stops the
            // next boot from trying, and a seed that imported nothing must get
            // another chance rather than look finished.
            log.error("Auto-import from '{}' incomplete — {} of {} ZIP file(s) failed. "
                    + "The successful ones are recorded and will not be re-imported; the "
                    + "failures will be retried on the next start.",
                    EXPORT_DIR, failed, zipFiles.length);
            return;
        }

        log.info("Auto-import from '{}' completed.", EXPORT_DIR);
        saveMarker(AUTO_IMPORT_DONE, "true");
    }

    /** Whether this ZIP landed on an earlier boot, so a retry must leave it alone. */
    private boolean alreadyImported(File zipFile) {
        return turConfigVarRepository.findById(fileMarkerId(zipFile)).isPresent();
    }

    /**
     * A stable key for one ZIP name, short enough for the 50-char id column. The
     * digest is of the file name only — the same seed re-mounted at a different
     * path is the same seed.
     */
    private static String fileMarkerId(File zipFile) {
        return FILE_MARKER_PREFIX + digest(zipFile.getName());
    }

    private static String digest(String name) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(name.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the platform; unreachable in practice.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private void saveMarker(String id, String value) {
        TurConfigVar marker = new TurConfigVar();
        marker.setId(id);
        marker.setPath(MARKER_PATH);
        marker.setValue(value);
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

    /**
     * @return whether the archive imported cleanly. A corrupt or malformed ZIP is
     *         reported by the returned {@code ImportResult}, not by an exception,
     *         so both have to be read to tell success from failure.
     */
    private boolean importZipFile(File zipFile) {
        log.info("Auto-importing '{}'...", zipFile.getName());
        try (FileInputStream input = new FileInputStream(zipFile)) {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    zipFile.getName(), zipFile.getName(),
                    "application/zip", IOUtils.toByteArray(input));
            TurImportExchange.ImportResult result =
                    turImportExchange.importFromMultipartFile(multipartFile, true, true, null);
            if (result != null && result.error() != null) {
                log.error("Failed to auto-import '{}': {}", zipFile.getName(), result.error());
                return false;
            }
            log.info("Successfully auto-imported '{}'.", zipFile.getName());
            return true;
        } catch (IOException | RuntimeException e) {
            log.error("Failed to auto-import '{}': {}", zipFile.getName(), e.getMessage(), e);
            return false;
        }
    }
}
