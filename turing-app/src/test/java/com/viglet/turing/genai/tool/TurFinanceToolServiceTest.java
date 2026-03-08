package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TurFinanceToolServiceTest {

    private TurFinanceToolService service;

    @BeforeEach
    void setUp() {
        service = new TurFinanceToolService();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void shouldNormalizeBlankRangeTo5d(String range) throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("normalizeRange", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, range);

        assertThat(result).isEqualTo("5d");
    }

    @Test
    void shouldKeepValidRange() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("normalizeRange", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "1mo")).isEqualTo("1mo");
    }

    @ParameterizedTest
    @CsvSource({
            "1d, 5m",
            "5d, 1d",
            "1mo, 1d",
            "3mo, 1wk",
            "6mo, 1wk",
            "1y, 1mo",
            "unknown, 1d"
    })
    void shouldMapRangeToInterval(String range, String expectedInterval) throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("mapRangeToInterval", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, range);

        assertThat(result).isEqualTo(expectedInterval);
    }

    @Test
    void shouldFormatHttpError() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatHttpError", String.class, int.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "AAPL", 404);

        assertThat(result)
                .contains("AAPL")
                .contains("HTTP 404")
                .contains("ticker symbol is correct");
    }

    @Test
    void shouldFormatVolumeInBillions() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatVolume", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, 1_500_000_000L);
        assertThat(result).matches("1[.,]5B");
    }

    @Test
    void shouldFormatVolumeInMillions() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatVolume", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, 2_500_000L);
        assertThat(result).matches("2[.,]5M");
    }

    @Test
    void shouldFormatVolumeInThousands() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatVolume", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, 50_000L);
        assertThat(result).matches("50[.,]0K");
    }

    @Test
    void shouldFormatVolumeRaw() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatVolume", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, 999L);
        assertThat(result).isEqualTo("999");
    }

    @Test
    void shouldFormatValueAsNA_whenArrayIsNull() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatValue",
                org.json.JSONArray.class, int.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, (org.json.JSONArray) null, 0);

        assertThat(result).isEqualTo("N/A");
    }

    @Test
    void shouldFormatValueWithDecimals() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatValue",
                org.json.JSONArray.class, int.class);
        method.setAccessible(true);

        org.json.JSONArray array = new org.json.JSONArray("[123.456]");
        String result = (String) method.invoke(service, array, 0);

        assertThat(result).matches("123[.,]46");
    }

    @Test
    void shouldReturnParseChartResult_null_whenResultsEmpty() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("parseChartResult",
                String.class, String.class);
        method.setAccessible(true);

        String body = "{\"chart\":{\"result\":[]}}";
        Object result = method.invoke(service, "TEST", body);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnHeaderInfoWithExchange() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHeaderInfo",
                StringBuilder.class, String.class, org.json.JSONObject.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("shortName", "Apple Inc.");
        meta.put("exchangeName", "NasdaqGS");
        meta.put("currency", "USD");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, "AAPL", meta);

        String result = sb.toString();
        assertThat(result)
                .contains("Symbol: AAPL")
                .contains("Apple Inc.")
                .contains("Exchange: NasdaqGS")
                .contains("Currency: USD");
    }

    @Test
    void shouldAppendCurrentQuoteWithChange() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendCurrentQuote",
                StringBuilder.class, org.json.JSONObject.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("regularMarketPrice", 150.0);
        meta.put("chartPreviousClose", 145.0);
        meta.put("regularMarketTime", 1700000000L);
        meta.put("currency", "USD");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, meta);

        String result = sb.toString();
        assertThat(result)
                .contains("Current Quote")
                .containsPattern("Price: 150[.,]00 USD")
                .containsPattern("Previous Close: 145[.,]00")
                .containsPattern("Change: \\+5[.,]00");
    }

    @Test
    void shouldSkipHistoricalData_whenNoTimestamp() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHistoricalData",
                StringBuilder.class, org.json.JSONObject.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject result = new org.json.JSONObject();
        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, result, "5d");

        assertThat(sb).isEmpty();
    }
}
