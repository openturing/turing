package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TurDateTimeToolServiceTest {

    private TurDateTimeToolService service;

    @BeforeEach
    void setUp() {
        service = new TurDateTimeToolService();
    }

    @Test
    void shouldReturnCurrentTimeForUtc() {
        String result = service.getCurrentTime("UTC");

        assertThat(result)
                .startsWith("Current date and time in UTC:")
                .contains("UTC");
    }

    @Test
    void shouldReturnCurrentTimeForSaoPaulo() {
        String result = service.getCurrentTime("America/Sao_Paulo");

        assertThat(result)
                .startsWith("Current date and time in America/Sao_Paulo:")
                .contains("America/Sao_Paulo");
    }

    @Test
    void shouldReturnCurrentTimeForTokyo() {
        String result = service.getCurrentTime("Asia/Tokyo");

        assertThat(result)
                .startsWith("Current date and time in Asia/Tokyo:")
                .contains("JST")
                .containsPattern("\\d{4}");
    }

    @Test
    void shouldReturnCurrentTimeForLondon() {
        String result = service.getCurrentTime("Europe/London");

        assertThat(result).startsWith("Current date and time in Europe/London:");
    }

    @Test
    void shouldReturnErrorForInvalidTimezone() {
        String result = service.getCurrentTime("Invalid/Zone");

        assertThat(result)
                .startsWith("Error: invalid timezone 'Invalid/Zone'")
                .contains("IANA format");
    }

    @Test
    void shouldReturnErrorForEmptyTimezone() {
        String result = service.getCurrentTime("");

        assertThat(result).startsWith("Error: invalid timezone ''");
    }

    @Test
    void shouldReturnErrorForNullTimezone() {
        String result = service.getCurrentTime(null);

        assertThat(result).startsWith("Error: invalid timezone 'null'");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)",
            "(January|February|March|April|May|June|July|August|September|October|November|December)",
            "\\d{2}:\\d{2}:\\d{2}"
    })
    void shouldContainExpectedPatterns(String pattern) {
        String result = service.getCurrentTime("UTC");

        assertThat(result).containsPattern(pattern);
    }
}
