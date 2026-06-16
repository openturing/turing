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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the working-directory-relative {@code store/} tree the products use
 * for scratch and on-disk assets, creating directories on demand.
 *
 * <p>Neutral home (Block Q / T375) for the store-directory helpers the three
 * products hand-synced identically. Deliberately neutral: no {@code Tur*}
 * naming and no dependency on any product module.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public final class VigletStoreDirectory {

    private static final String USER_DIR = "user.dir";
    private static final File USER_DIRECTORY = new File(System.getProperty(USER_DIR));
    private static final String STORE = "store";
    private static final String TMP = "tmp";

    private VigletStoreDirectory() {
        throw new IllegalStateException("Utility class");
    }

    public static File getStoreDir() {
        File store = new File(USER_DIRECTORY.getAbsolutePath().concat(File.separator + STORE));
        createDirectories(store);
        return store;
    }

    public static File addSubDirToStoreDir(String directoryName) {
        File storeDir = getStoreDir();
        File newDir = new File(storeDir.getAbsolutePath().concat(File.separator + directoryName));
        createDirectories(newDir);
        return newDir;
    }

    public static File getTempDirectory() {
        return addSubDirToStoreDir(TMP);
    }

    private static void createDirectories(File dir) {
        try {
            Files.createDirectories(dir.toPath());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}
