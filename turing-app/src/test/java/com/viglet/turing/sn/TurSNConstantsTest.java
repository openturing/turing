package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

/**
 * Tests for TurSNConstants.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSNConstantsTest {

    @Test
    void testConstructorThrowsException() throws NoSuchMethodException {
        Constructor<TurSNConstants> constructor = TurSNConstants.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Semantic Navigation Constants class");
    }

    @Test
    void testFileProtocolConstant() {
        assertThat(TurSNConstants.FILE_PROTOCOL).isEqualTo("file://");
    }

    @Test
    void testExportFileConstant() {
        assertThat(TurSNConstants.EXPORT_FILE).isEqualTo("export.json");
    }

    @Test
    void testIndexingQueueConstant() {
        assertThat(TurSNConstants.INDEXING_QUEUE).isEqualTo("indexing.queue");
    }

    @Test
    void testIndexingQueueListenerConstant() {
        assertThat(TurSNConstants.INDEXING_QUEUE_LISTENER).isEqualTo("indexingQueueListener");
    }

    @Test
    void testAllConstantsAreNotNullAndNotEmpty() {
        assertThat(TurSNConstants.FILE_PROTOCOL).isNotNull().isNotEmpty();
        assertThat(TurSNConstants.EXPORT_FILE).isNotNull().isNotEmpty();
        assertThat(TurSNConstants.INDEXING_QUEUE).isNotNull().isNotEmpty();
        assertThat(TurSNConstants.INDEXING_QUEUE_LISTENER).isNotNull().isNotEmpty();
    }

    @Test
    void testFileProtocolStartsWithFileScheme() {
        assertThat(TurSNConstants.FILE_PROTOCOL).startsWith("file:");
    }

    @Test
    void testExportFileEndsWithJson() {
        assertThat(TurSNConstants.EXPORT_FILE).endsWith(".json");
    }

    @Test
    void testIndexingQueueContainsQueue() {
        assertThat(TurSNConstants.INDEXING_QUEUE).contains("queue");
    }

    @Test
    void testIndexingQueueListenerContainsListener() {
        assertThat(TurSNConstants.INDEXING_QUEUE_LISTENER).contains("Listener");
    }

    @Test
    void testIndexingQueueListenerIsCamelCase() {
        assertThat(TurSNConstants.INDEXING_QUEUE_LISTENER)
                .matches("[a-z][a-zA-Z]*");
    }

    @Test
    void testFileProtocolHasDoubleSlash() {
        assertThat(TurSNConstants.FILE_PROTOCOL).endsWith("//");
    }
}
