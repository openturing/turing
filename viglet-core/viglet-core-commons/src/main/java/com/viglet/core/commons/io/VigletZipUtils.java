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
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.core.commons.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.FileUtils;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;

/**
 * Zip a directory tree and extract a zip archive.
 *
 * <p>Neutral home (Block Q / T375) for the ZIP add/extract the three products
 * hand-synced identically (commons-compress for writing, zip4j for extraction).
 * Deliberately neutral: no {@code Tur*} naming and no dependency on any product
 * module.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public final class VigletZipUtils {

    private VigletZipUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Add all files from the source directory to the destination zip file.
     *
     * @param source      the directory with files to add
     * @param destination the zip file that should contain the files
     */
    public static void addFilesToZip(File source, File destination) {
        try (OutputStream archiveStream = Files.newOutputStream(destination.toPath());
                ArchiveOutputStream<ZipArchiveEntry> archive = new ArchiveStreamFactory()
                        .createArchiveOutputStream(ArchiveStreamFactory.ZIP, archiveStream)) {

            FileUtils.listFiles(source, null, true)
                    .forEach(file -> addFileToZip(source, archive, file));

            archive.finish();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void addFileToZip(File source, ArchiveOutputStream<ZipArchiveEntry> archive,
            File file) {
        try {
            String entryName = getEntryName(source, file);
            ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
            archive.putArchiveEntry(entry);

            try (BufferedInputStream input =
                    new BufferedInputStream(Files.newInputStream(file.toPath()))) {
                input.transferTo(archive);
                archive.closeArchiveEntry();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Remove the leading part of each entry that contains the source directory
     * name.
     *
     * @param source the directory where the file entry is found
     * @param file   the file that is about to be added
     * @return the name of an archive entry
     */
    private static String getEntryName(File source, File file) {
        Path sourcePath = source.toPath().toAbsolutePath().normalize();
        Path filePath = file.toPath().toAbsolutePath().normalize();
        return sourcePath.relativize(filePath).toString();
    }

    /**
     * Unzip a file into the output folder.
     *
     * @param file         input zip file
     * @param outputFolder output folder
     */
    public static void unZipIt(File file, File outputFolder) {
        try (ZipFile zipFile = new ZipFile(file)) {
            zipFile.extractAll(outputFolder.getAbsolutePath());
        } catch (IllegalStateException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}
