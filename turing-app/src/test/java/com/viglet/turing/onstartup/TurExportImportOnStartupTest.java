/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.onstartup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.exchange.TurImportExchange;
import com.viglet.turing.exchange.TurImportExchange.ImportResult;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

/**
 * T949 / §XIV.8.8 — a boot that imported nothing must not look like one that
 * imported everything.
 *
 * <p>There was no test for this runner at all, which is how it could write its
 * one-shot marker after catching every failure. The marker is what stops the
 * next boot from trying, so a corrupt seed disabled seeding for that database
 * permanently — and since T373 the seed carries the only search engine and
 * vector store a fresh Cloud deployment has.
 *
 * <p>The runner reads {@code user.dir} to find {@code export/}, so each test
 * points it at a temp directory and restores it afterwards.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurExportImportOnStartupTest {

    private static final String DONE = "EXPORT_AUTO_IMPORT";

    @Mock
    private TurConfigVarRepository turConfigVarRepository;
    @Mock
    private TurImportExchange turImportExchange;

    private TurExportImportOnStartup runner;
    private String previousUserDir;
    private Path exportDir;

    /** Marker ids the runner saved, in order. */
    private final List<String> saved = new ArrayList<>();

    @BeforeEach
    void setUp(@TempDir Path workingDir) throws IOException {
        previousUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", workingDir.toString());
        exportDir = Files.createDirectory(workingDir.resolve("export"));

        runner = new TurExportImportOnStartup(turConfigVarRepository, turImportExchange);

        when(turConfigVarRepository.findById(anyString())).thenReturn(Optional.empty());
        when(turConfigVarRepository.save(any(TurConfigVar.class))).thenAnswer(invocation -> {
            TurConfigVar var = invocation.getArgument(0);
            saved.add(var.getId());
            return var;
        });
    }

    @AfterEach
    void restoreUserDir() {
        System.setProperty("user.dir", previousUserDir);
    }

    private void zip(String name) throws IOException {
        Files.write(exportDir.resolve(name), new byte[] {1, 2, 3});
    }

    private void importReturns(ImportResult... results) {
        var stub = when(turImportExchange.importFromMultipartFile(any(), anyBoolean(),
                anyBoolean(), any()));
        for (ImportResult result : results) {
            stub = stub.thenReturn(result);
        }
    }

    private static ImportResult ok() {
        return new ImportResult(true, "site", 0, 0, false, null, 0, null, List.of(), true,
                null, List.of());
    }

    private void run() {
        runner.run(null);
    }

    private List<String> fileMarkers() {
        return saved.stream().filter(id -> !DONE.equals(id)).toList();
    }

    // ---- the happy path is unchanged --------------------------------------

    @Test
    void marksTheDirectoryDoneWhenEveryZipImports() throws IOException {
        zip("a.zip");
        zip("b.zip");
        importReturns(ok(), ok());

        run();

        assertThat(saved).contains(DONE);
        assertThat(fileMarkers()).hasSize(2);
    }

    @Test
    void doesNothingWhenTheDirectoryIsAlreadyMarkedDone() throws IOException {
        zip("a.zip");
        when(turConfigVarRepository.findById(DONE))
                .thenReturn(Optional.of(new TurConfigVar()));

        run();

        verify(turImportExchange, never()).importFromMultipartFile(any(), anyBoolean(),
                anyBoolean(), any());
        assertThat(saved).isEmpty();
    }

    // ---- the defect --------------------------------------------------------

    @Test
    void doesNotMarkTheDirectoryDoneWhenTheOnlyZipFails() throws IOException {
        zip("seed.zip");
        importReturns(ImportResult.error("Failed to extract ZIP file"));

        run();

        assertThat(saved)
                .as("the marker is what stops the next boot; a seed that imported "
                        + "nothing must get another chance")
                .doesNotContain(DONE);
        assertThat(fileMarkers()).isEmpty();
    }

    @Test
    void treatsAThrownFailureTheSameAsAReportedOne() throws IOException {
        zip("seed.zip");
        when(turImportExchange.importFromMultipartFile(any(), anyBoolean(), anyBoolean(), any()))
                .thenThrow(new IllegalStateException("boom"));

        run();

        assertThat(saved).doesNotContain(DONE);
        assertThat(fileMarkers()).isEmpty();
    }

    @Test
    void recordsTheSuccessesOfAPartialRunButNotTheDirectory() throws IOException {
        zip("a.zip");
        zip("b.zip");
        importReturns(ok(), ImportResult.error("Missing required export.json in ZIP"));

        run();

        assertThat(saved).doesNotContain(DONE);
        assertThat(fileMarkers())
                .as("a re-import deletes and recreates the site, so a success must not "
                        + "be repeated on the retry")
                .hasSize(1);
    }

    @Test
    void skipsAZipAnEarlierBootAlreadyImported() throws IOException {
        zip("a.zip");
        zip("b.zip");
        importReturns(ok());

        // Whatever key the runner derives for a.zip, pretend it is on record.
        ArgumentCaptor<String> lookups = ArgumentCaptor.forClass(String.class);
        when(turConfigVarRepository.findById(anyString())).thenAnswer(invocation -> {
            String id = invocation.getArgument(0);
            return id.equals(markerFor("a.zip")) ? Optional.of(new TurConfigVar())
                    : Optional.empty();
        });

        run();

        verify(turImportExchange).importFromMultipartFile(any(), anyBoolean(), anyBoolean(),
                any());
        verify(turConfigVarRepository, org.mockito.Mockito.atLeastOnce())
                .findById(lookups.capture());
        assertThat(lookups.getAllValues()).contains(markerFor("a.zip"));
        assertThat(saved).contains(DONE);
    }

    /**
     * The marker id the runner derives, computed the same way here so the test
     * pins the contract (stable per file name) without reaching into the class.
     */
    private static String markerFor(String zipName) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(zipName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return "EXPORT_AUTO_IMPORT_" + hex;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void everyMarkerIdFitsTheIdColumn() throws IOException {
        // TurConfigVar.id is length = 50. A ZIP name is not bounded, so the key
        // carries a digest — this is what proves the digest is not decorative.
        zip("a-very-long-seed-archive-name-that-would-not-fit-in-fifty-characters.zip");
        importReturns(ok());

        run();

        assertThat(fileMarkers()).allSatisfy(id -> assertThat(id.length()).isLessThanOrEqualTo(50));
    }

    // ---- the early returns that must stay ----------------------------------

    @Test
    void writesNoMarkerWhenTheExportDirectoryIsEmpty() {
        run();

        assertThat(saved)
                .as("an empty directory is 'nothing to do', not 'done' — a ZIP mounted "
                        + "later still has to run")
                .isEmpty();
        verify(turImportExchange, never()).importFromMultipartFile(any(), anyBoolean(),
                anyBoolean(), any());
    }
}
