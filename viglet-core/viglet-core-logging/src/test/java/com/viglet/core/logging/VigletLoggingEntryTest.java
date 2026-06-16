/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.Test;

class VigletLoggingEntryTest {

    @Test
    void shouldBuildAndExposeGeneralLogFields() {
        Date now = new Date();
        VigletLoggingEntry log = VigletLoggingEntry.builder()
                .clusterNode("node-1")
                .level("INFO")
                .logger("com.viglet.Logger")
                .message("message")
                .stackTrace("stack")
                .date(now)
                .build();

        assertThat(log.getClusterNode()).isEqualTo("node-1");
        assertThat(log.getLevel()).isEqualTo("INFO");
        assertThat(log.getLogger()).isEqualTo("com.viglet.Logger");
        assertThat(log.getMessage()).isEqualTo("message");
        assertThat(log.getStackTrace()).isEqualTo("stack");
        assertThat(log.getDate()).isEqualTo(now);
    }
}
