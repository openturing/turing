package com.viglet.turing.client.ocr.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URI;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import com.viglet.turing.client.auth.TurServer;
import com.viglet.turing.client.ocr.TurOcr;
import com.viglet.turing.commons.file.TurFileAttributes;

class TurClientOcrSampleTest {

    @Test
    void shouldProcessHttpUrlBranchViaPrivateHelper() throws Exception {
        TurFileAttributes expected = TurFileAttributes.builder().name("http-result").build();

        try (MockedConstruction<TurOcr> mockedConstruction = mockConstruction(TurOcr.class,
                (mock, context) -> when(mock.processUrl(any(TurServer.class), any(URI.class), eq(false)))
                        .thenReturn(expected))) {
            TurFileAttributes result = invokeGetAttributes("http://localhost:2700", "api-key",
                    "http://example.com/file.pdf");

            assertThat(result).isSameAs(expected);
            verify(mockedConstruction.constructed().getFirst()).processUrl(any(TurServer.class),
                    eq(URI.create("http://example.com/file.pdf")), eq(false));
        }
    }

    @Test
    void shouldProcessFileBranchViaPrivateHelper() throws Exception {
        TurFileAttributes expected = TurFileAttributes.builder().name("file-result").build();

        try (MockedConstruction<TurOcr> mockedConstruction = mockConstruction(TurOcr.class,
                (mock, context) -> when(mock.processFile(any(TurServer.class), any(File.class), eq(false)))
                        .thenReturn(expected))) {
            TurFileAttributes result = invokeGetAttributes("http://localhost:2700", "api-key", "C:/tmp/sample.pdf");

            assertThat(result).isSameAs(expected);
            verify(mockedConstruction.constructed().getFirst()).processFile(any(TurServer.class),
                    argThat(file -> file.getPath().replace('\\', '/').endsWith("tmp/sample.pdf")), eq(false));
        }
    }

    @Test
    void shouldHandleMainArgumentsBranches() {
        TurClientOcrSample.main(new String[] {});

        TurFileAttributes expected = TurFileAttributes.builder().name("main-result").build();
        try (MockedConstruction<TurOcr> mockedConstruction = mockConstruction(TurOcr.class,
                (mock, context) -> when(mock.processUrl(any(TurServer.class), any(URI.class), eq(false)))
                        .thenReturn(expected))) {
            TurClientOcrSample
                    .main(new String[] { "http://localhost:2700", "api-key", "unused", "http://example.com/doc.pdf" });

            verify(mockedConstruction.constructed().getFirst()).processUrl(any(TurServer.class),
                    eq(URI.create("http://example.com/doc.pdf")), eq(false));
        }
    }

    private static TurFileAttributes invokeGetAttributes(String turingUrl, String apiKey, String fileUrl)
            throws Exception {
        Method method = TurClientOcrSample.class.getDeclaredMethod("getAttributes", String.class, String.class,
                String.class);
        method.setAccessible(true);
        return (TurFileAttributes) method.invoke(null, turingUrl, apiKey, fileUrl);
    }
}