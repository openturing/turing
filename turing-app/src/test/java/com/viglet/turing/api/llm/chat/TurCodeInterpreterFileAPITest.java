package com.viglet.turing.api.llm.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.viglet.turing.genai.tool.TurCodeInterpreterUrlSigner;
import com.viglet.turing.genai.tool.TurCodeInterpreterUrlSigner.VerifyResult;

class TurCodeInterpreterFileAPITest {

    private TurCodeInterpreterFileAPI api;

    @BeforeEach
    void setUp() {
        // Sign-pass-through signer: every verify() returns OK so these
        // tests focus on the path-sanitization layer they were written
        // for, not on the HMAC gate (covered in TurCodeInterpreterUrlSignerTest).
        TurCodeInterpreterUrlSigner signer = mock(TurCodeInterpreterUrlSigner.class);
        when(signer.verify(any(), any(), any(), any(), any())).thenReturn(VerifyResult.OK);
        api = new TurCodeInterpreterFileAPI(signer);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "..\\windows\\system32",
            "session/../../etc",
            "session\\..\\..\\etc"
    })
    void shouldRejectPathTraversalInSessionId(String sessionId) {
        ResponseEntity<?> response = api.getFile(sessionId, "file.txt", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../secret.txt",
            "..\\secret.txt",
            "subdir/file.txt",
            "subdir\\file.txt"
    })
    void shouldRejectPathTraversalInFilename(String filename) {
        ResponseEntity<?> response = api.getFile("valid-session", filename, null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnNotFoundForMissingFile() {
        // Valid session/filename but no actual file on disk
        ResponseEntity<?> response = api.getFile("nonexistent-session", "missing.txt", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectSessionIdWithSlash() {
        ResponseEntity<?> response = api.getFile("a/b", "file.txt", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectSessionIdWithBackslash() {
        ResponseEntity<?> response = api.getFile("a\\b", "file.txt", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectSessionIdWithDoubleDot() {
        ResponseEntity<?> response = api.getFile("..", "file.txt", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectFilenameWithDoubleDot() {
        ResponseEntity<?> response = api.getFile("session", "..file", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldAcceptCleanSessionIdAndFilename() {
        // Clean input but file doesn't exist -> NOT_FOUND (not BAD_REQUEST)
        ResponseEntity<?> response = api.getFile("abc12345", "output.png", null, null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectWhenSignerVerifyFails() {
        TurCodeInterpreterUrlSigner failingSigner = mock(TurCodeInterpreterUrlSigner.class);
        when(failingSigner.verify(any(), any(), any(), any(), any()))
                .thenReturn(VerifyResult.COOKIE_MISMATCH);
        TurCodeInterpreterFileAPI gatedApi = new TurCodeInterpreterFileAPI(failingSigner);

        ResponseEntity<?> response = gatedApi.getFile("abc12345", "output.png",
                "9999999999", "deadbeef", "wrong-conv");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
