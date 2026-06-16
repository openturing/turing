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
package com.viglet.core.commons.file;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VigletFileSizeTest {

    @Test
    void emptyConstructorIsAllZero() {
        VigletFileSize size = new VigletFileSize();
        assertThat(size.getBytes()).isZero();
        assertThat(size.getKiloBytes()).isZero();
        assertThat(size.getMegaBytes()).isZero();
    }

    @Test
    void convertsBytesToKiloAndMega() {
        VigletFileSize size = new VigletFileSize(1024.0f);
        assertThat(size.getBytes()).isEqualTo(1024.0f);
        assertThat(size.getKiloBytes()).isEqualTo(1.0f);
        assertThat(size.getMegaBytes()).isEqualTo(0.0f);
    }

    @Test
    void roundsToTwoDecimals() {
        VigletFileSize size = new VigletFileSize(1536.0f);
        assertThat(size.getKiloBytes()).isEqualTo(1.5f);
    }

    @Test
    void explicitAllArgsConstructorBypassesConversion() {
        VigletFileSize size = new VigletFileSize(1024.0f, 1.0f, 0.001f);
        assertThat(size.getBytes()).isEqualTo(1024.0f);
        assertThat(size.getKiloBytes()).isEqualTo(1.0f);
        assertThat(size.getMegaBytes()).isEqualTo(0.001f);
    }
}
