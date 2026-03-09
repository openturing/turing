package com.viglet.turing.commons.logging;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

class TurLoggingIndexingLogTest {

    @Test
    void utilityConstructorShouldThrow() throws Exception {
        Constructor<TurLoggingIndexingLog> constructor = TurLoggingIndexingLog.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Expected InvocationTargetException");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(ex.getCause().getMessage()).isEqualTo("Log Ingestion Utility");
        }
    }

    @Test
    void shouldAcceptStatusWithoutThrowing() {
        TurLoggingIndexing status = TurLoggingIndexing.builder().contentId("doc-1").build();

        TurLoggingIndexingLog.setStatus(status);

        assertThat(status.getContentId()).isEqualTo("doc-1");
    }
}
