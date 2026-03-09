package com.viglet.turing.commons.logging;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.spi.ILoggingEvent;

class TurMongoDBAppenderBaseTest {

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

    private static class TestAppender extends TurMongoDBAppenderBase {
        @Override
        protected void append(ILoggingEvent iLoggingEvent) {
            // Test stub: no-op implementation since this test only validates
            // that the appender can start and stop safely, not actual logging behavior
        }
    }
}
