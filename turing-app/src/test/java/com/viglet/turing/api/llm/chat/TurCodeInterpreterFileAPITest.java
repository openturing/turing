package com.viglet.turing.api.llm.chat;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class TurCodeInterpreterFileAPITest {

    private TurCodeInterpreterFileAPI api;

    @BeforeEach
    void setUp() {
        api = new TurCodeInterpreterFileAPI();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "..\\windows\\system32",
            "session/../../etc",
            "session\\..\\..\\etc"
    })
    void shouldRejectPathTraversalInSessionId(String sessionId) {
        ResponseEntity<?> response = api.getFile(sessionId, "file.txt");

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
        ResponseEntity<?> response = api.getFile("valid-session", filename);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturnNotFoundForMissingFile() {
        // Valid session/filename but no actual file on disk
        ResponseEntity<?> response = api.getFile("nonexistent-session", "missing.txt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldRejectSessionIdWithSlash() {
        ResponseEntity<?> response = api.getFile("a/b", "file.txt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectSessionIdWithBackslash() {
        ResponseEntity<?> response = api.getFile("a\\b", "file.txt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectSessionIdWithDoubleDot() {
        ResponseEntity<?> response = api.getFile("..", "file.txt");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldRejectFilenameWithDoubleDot() {
        ResponseEntity<?> response = api.getFile("session", "..file");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldAcceptCleanSessionIdAndFilename() {
        // Clean input but file doesn't exist -> NOT_FOUND (not BAD_REQUEST)
        ResponseEntity<?> response = api.getFile("abc12345", "output.png");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
