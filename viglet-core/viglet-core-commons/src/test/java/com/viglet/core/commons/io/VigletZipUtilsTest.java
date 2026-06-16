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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VigletZipUtilsTest {

    @Test
    void zipsADirectoryAndUnzipsItBack(@TempDir Path tmp) throws Exception {
        Path source = Files.createDirectories(tmp.resolve("source"));
        Files.writeString(source.resolve("hello.txt"), "hello world");
        Files.createDirectories(source.resolve("nested"));
        Files.writeString(source.resolve("nested/inner.txt"), "inner");

        File zip = tmp.resolve("out.zip").toFile();
        VigletZipUtils.addFilesToZip(source.toFile(), zip);
        assertThat(zip).exists();
        assertThat(zip.length()).isPositive();

        File outputFolder = tmp.resolve("extracted").toFile();
        VigletZipUtils.unZipIt(zip, outputFolder);

        assertThat(new File(outputFolder, "hello.txt")).exists();
        assertThat(Files.readString(outputFolder.toPath().resolve("hello.txt")))
                .isEqualTo("hello world");
        assertThat(new File(outputFolder, "nested/inner.txt")).exists();
    }
}
