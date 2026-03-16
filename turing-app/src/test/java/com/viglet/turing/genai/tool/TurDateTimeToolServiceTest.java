package com.viglet.turing.genai.tool;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TurDateTimeToolServiceTest {

    private final TurDateTimeToolService service = new TurDateTimeToolService();

    @Test
    void getCurrentTimeShouldReturnFormattedTimeForUTC() {
        String result = service.getCurrentTime("UTC");
        assertTrue(result.startsWith("Current date and time in UTC:"));
    }

    @Test
    void getCurrentTimeShouldReturnFormattedTimeForSaoPaulo() {
        String result = service.getCurrentTime("America/Sao_Paulo");
        assertTrue(result.startsWith("Current date and time in America/Sao_Paulo:"));
    }

    @Test
    void getCurrentTimeShouldReturnFormattedTimeForTokyo() {
        String result = service.getCurrentTime("Asia/Tokyo");
        assertTrue(result.contains("Asia/Tokyo"));
    }

    @Test
    void getCurrentTimeShouldReturnErrorForInvalidTimezone() {
        String result = service.getCurrentTime("Invalid/Timezone");
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("invalid timezone"));
    }

    @Test
    void getCurrentTimeShouldReturnErrorForNullTimezone() {
        String result = service.getCurrentTime(null);
        assertTrue(result.startsWith("Error:"));
    }
}
