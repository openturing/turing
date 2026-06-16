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

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;

class VigletMongoDBAppenderBaseTest {

    @Test
    void shouldStartAndStopSafelyEvenWithInvalidConfiguration() {
        TestAppender appender = new TestAppender();
        appender.setEnabled(true);
        appender.setConnectionString("mongodb://invalid-host:27017");
        appender.setDatabaseName("testdb");
        appender.setCollectionName("logs");

        assertThatCode(appender::start).doesNotThrowAnyException();
        assertThatCode(appender::stop).doesNotThrowAnyException();
    }

    private static class TestAppender extends VigletMongoDBAppenderBase {
        @Override
        protected void append(ILoggingEvent iLoggingEvent) {
            // Test stub: no-op implementation since this test only validates
            // that the appender can start and stop safely, not actual logging behavior
        }
    }
}
