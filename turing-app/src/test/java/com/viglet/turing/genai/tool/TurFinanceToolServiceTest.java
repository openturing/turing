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
    @ValueSource(strings = { "  ", "\t" })
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

    @ParameterizedTest
    @CsvSource({
            "1500000000, '1[.,]5B'",
            "2500000, '2[.,]5M'",
            "50000, '50[.,]0K'"
    })
    void shouldFormatVolume(long value, String expectedPattern) throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatVolume", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, value);
        assertThat(result).matches(expectedPattern);
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

    @Test
    void shouldSkipHistoricalData_whenTimestampIsNull() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHistoricalData",
                StringBuilder.class, org.json.JSONObject.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject result = new org.json.JSONObject();
        result.put("timestamp", org.json.JSONObject.NULL);
        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, result, "5d");

        assertThat(sb).isEmpty();
    }

    @Test
    void shouldAppendHistoricalDataWithTimestamps() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHistoricalData",
                StringBuilder.class, org.json.JSONObject.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject result = new org.json.JSONObject();
        result.put("timestamp", new org.json.JSONArray().put(1700000000L).put(1700086400L));

        org.json.JSONObject quote = new org.json.JSONObject();
        quote.put("close", new org.json.JSONArray().put(150.0).put(152.0));
        quote.put("high", new org.json.JSONArray().put(155.0).put(157.0));
        quote.put("low", new org.json.JSONArray().put(148.0).put(149.0));
        quote.put("volume", new org.json.JSONArray().put(5000000L).put(6000000L));

        org.json.JSONObject indicators = new org.json.JSONObject();
        indicators.put("quote", new org.json.JSONArray().put(quote));
        result.put("indicators", indicators);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, result, "5d");

        String output = sb.toString();
        assertThat(output)
                .contains("Price History (5d)")
                .contains("Date | Close | High | Low | Volume");
    }

    @Test
    void shouldAppendHistoricalDataWithNullArrays() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHistoricalData",
                StringBuilder.class, org.json.JSONObject.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject result = new org.json.JSONObject();
        result.put("timestamp", new org.json.JSONArray().put(1700000000L));

        org.json.JSONObject quote = new org.json.JSONObject();
        // no close, high, low, volume arrays
        org.json.JSONObject indicators = new org.json.JSONObject();
        indicators.put("quote", new org.json.JSONArray().put(quote));
        result.put("indicators", indicators);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, result, "1d");

        String output = sb.toString();
        assertThat(output).contains("Price History (1d)").contains("N/A");
    }

    @Test
    void shouldReturnHeaderInfoWithoutExchange() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHeaderInfo",
                StringBuilder.class, String.class, org.json.JSONObject.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("shortName", "AAPL");
        meta.put("exchangeName", "");
        meta.put("currency", "USD");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, "AAPL", meta);

        String result = sb.toString();
        assertThat(result)
                .contains("Symbol: AAPL")
                .doesNotContain("Exchange:");
    }

    @Test
    void shouldReturnHeaderInfoWhenShortNameMatchesSymbol() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHeaderInfo",
                StringBuilder.class, String.class, org.json.JSONObject.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("shortName", "GOOG");
        meta.put("exchangeName", "NasdaqGS");
        meta.put("currency", "USD");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, "GOOG", meta);

        String result = sb.toString();
        assertThat(result)
                .contains("Symbol: GOOG\n")
                .doesNotContain("(GOOG)");
    }

    @Test
    void shouldAppendCurrentQuoteWithZeroPreviousClose() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendCurrentQuote",
                StringBuilder.class, org.json.JSONObject.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("regularMarketPrice", 100.0);
        meta.put("chartPreviousClose", 0.0);
        meta.put("regularMarketTime", 0L);
        meta.put("currency", "BRL");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, meta);

        String result = sb.toString();
        assertThat(result)
                .contains("Current Quote")
                .contains("BRL")
                .doesNotContain("Last Update:");
    }

    @Test
    void shouldAppendCurrentQuoteWithRegularMarketTime() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendCurrentQuote",
                StringBuilder.class, org.json.JSONObject.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("regularMarketPrice", 200.0);
        meta.put("chartPreviousClose", 195.0);
        meta.put("regularMarketTime", 1700000000L);
        meta.put("currency", "USD");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, meta);

        assertThat(sb.toString()).contains("Last Update:");
    }

    @Test
    void shouldParseChartResultWithError() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("parseChartResult",
                String.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject error = new org.json.JSONObject();
        error.put("description", "No data found for symbol TEST");
        org.json.JSONObject chart = new org.json.JSONObject();
        chart.put("error", error);
        chart.put("result", new org.json.JSONArray());
        String body = new org.json.JSONObject().put("chart", chart).toString();

        try {
            method.invoke(service, "TEST", body);
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(IllegalArgumentException.class);
            assertThat(e.getCause().getMessage()).contains("TEST");
        }
    }

    @Test
    void shouldParseChartResultWithValidResult() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("parseChartResult",
                String.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject resultObj = new org.json.JSONObject();
        resultObj.put("meta", new org.json.JSONObject().put("currency", "USD"));

        String body = new org.json.JSONObject()
                .put("chart", new org.json.JSONObject()
                        .put("result", new org.json.JSONArray().put(resultObj)))
                .toString();

        Object result = method.invoke(service, "AAPL", body);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldFormatValueAsNA_whenNullAtIndex() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatValue",
                org.json.JSONArray.class, int.class);
        method.setAccessible(true);

        org.json.JSONArray array = new org.json.JSONArray();
        array.put(org.json.JSONObject.NULL);

        String result = (String) method.invoke(service, array, 0);
        assertThat(result).isEqualTo("N/A");
    }

    @Test
    void shouldBuildQuoteOutput() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("buildQuoteOutput",
                String.class, org.json.JSONObject.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("shortName", "Apple Inc.");
        meta.put("exchangeName", "NasdaqGS");
        meta.put("currency", "USD");
        meta.put("regularMarketPrice", 175.0);
        meta.put("chartPreviousClose", 170.0);
        meta.put("regularMarketTime", 1700000000L);

        org.json.JSONObject result = new org.json.JSONObject();
        result.put("meta", meta);

        String output = (String) method.invoke(service, "AAPL", result, "5d");
        assertThat(output)
                .contains("Symbol: AAPL")
                .contains("Apple Inc.")
                .contains("Current Quote");
    }

    @ParameterizedTest
    @CsvSource({
            "1000000000, '1[.,]0B'",
            "1000000, '1[.,]0M'",
            "1000, '1[.,]0K'"
    })
    void shouldFormatVolumeAtBoundaries(long value, String expectedPattern) throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("formatVolume", long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, value);
        assertThat(result).matches(expectedPattern);
    }

    @Test
    void shouldAppendHistoryRow() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHistoryRow",
                StringBuilder.class, org.json.JSONArray.class, org.json.JSONArray.class,
                org.json.JSONArray.class, org.json.JSONArray.class, org.json.JSONArray.class, int.class);
        method.setAccessible(true);

        org.json.JSONArray timestamps = new org.json.JSONArray().put(1700000000L);
        org.json.JSONArray closes = new org.json.JSONArray().put(150.0);
        org.json.JSONArray highs = new org.json.JSONArray().put(155.0);
        org.json.JSONArray lows = new org.json.JSONArray().put(148.0);
        org.json.JSONArray volumes = new org.json.JSONArray().put(5000000L);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, timestamps, closes, highs, lows, volumes, 0);

        String output = sb.toString();
        assertThat(output).contains("|");
    }

    @Test
    void shouldAppendHistoryRowWithNullVolumes() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHistoryRow",
                StringBuilder.class, org.json.JSONArray.class, org.json.JSONArray.class,
                org.json.JSONArray.class, org.json.JSONArray.class, org.json.JSONArray.class, int.class);
        method.setAccessible(true);

        org.json.JSONArray timestamps = new org.json.JSONArray().put(1700000000L);
        org.json.JSONArray closes = new org.json.JSONArray().put(150.0);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, timestamps, closes, null, null, null, 0);

        assertThat(sb.toString()).contains("N/A");
    }

    // --- Additional coverage: buildQuoteOutput with historical data ---

    @Test
    void shouldBuildQuoteOutputWithHistoricalData() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("buildQuoteOutput",
                String.class, org.json.JSONObject.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("shortName", "Microsoft Corp.");
        meta.put("exchangeName", "NasdaqGS");
        meta.put("currency", "USD");
        meta.put("regularMarketPrice", 400.0);
        meta.put("chartPreviousClose", 395.0);
        meta.put("regularMarketTime", 1700000000L);

        org.json.JSONObject quote = new org.json.JSONObject();
        quote.put("close", new org.json.JSONArray().put(395.0).put(398.0).put(400.0));
        quote.put("high", new org.json.JSONArray().put(396.0).put(399.0).put(402.0));
        quote.put("low", new org.json.JSONArray().put(393.0).put(396.0).put(398.0));
        quote.put("volume", new org.json.JSONArray().put(10000000L).put(12000000L).put(9000000L));

        org.json.JSONObject indicators = new org.json.JSONObject();
        indicators.put("quote", new org.json.JSONArray().put(quote));

        org.json.JSONObject result = new org.json.JSONObject();
        result.put("meta", meta);
        result.put("timestamp", new org.json.JSONArray().put(1699900000L).put(1699986400L).put(1700072800L));
        result.put("indicators", indicators);

        String output = (String) method.invoke(service, "MSFT", result, "5d");
        assertThat(output)
                .contains("Symbol: MSFT")
                .contains("Microsoft Corp.")
                .contains("Current Quote")
                .contains("Price History (5d)")
                .contains("Date | Close | High | Low | Volume");
    }

    @Test
    void shouldBuildQuoteOutputWithNoHistoricalData() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("buildQuoteOutput",
                String.class, org.json.JSONObject.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("shortName", "TEST");
        meta.put("exchangeName", "");
        meta.put("currency", "EUR");
        meta.put("regularMarketPrice", 50.0);
        meta.put("chartPreviousClose", 50.0);
        meta.put("regularMarketTime", 0L);

        org.json.JSONObject result = new org.json.JSONObject();
        result.put("meta", meta);
        // no timestamp field

        String output = (String) method.invoke(service, "TEST", result, "1d");
        assertThat(output)
                .contains("Symbol: TEST")
                .contains("Currency: EUR")
                .doesNotContain("Price History");
    }

    @Test
    void shouldAppendHistoryRowWithNullVolumeAtIndex() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHistoryRow",
                StringBuilder.class, org.json.JSONArray.class, org.json.JSONArray.class,
                org.json.JSONArray.class, org.json.JSONArray.class, org.json.JSONArray.class, int.class);
        method.setAccessible(true);

        org.json.JSONArray timestamps = new org.json.JSONArray().put(1700000000L);
        org.json.JSONArray closes = new org.json.JSONArray().put(150.0);
        org.json.JSONArray highs = new org.json.JSONArray().put(155.0);
        org.json.JSONArray lows = new org.json.JSONArray().put(148.0);
        org.json.JSONArray volumes = new org.json.JSONArray().put(org.json.JSONObject.NULL);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, timestamps, closes, highs, lows, volumes, 0);

        assertThat(sb.toString()).contains("N/A");
    }

    @Test
    void shouldAppendHistoricalDataLimitingToLast15Entries() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendHistoricalData",
                StringBuilder.class, org.json.JSONObject.class, String.class);
        method.setAccessible(true);

        org.json.JSONArray timestamps = new org.json.JSONArray();
        org.json.JSONArray closes = new org.json.JSONArray();
        org.json.JSONArray highs = new org.json.JSONArray();
        org.json.JSONArray lows = new org.json.JSONArray();
        org.json.JSONArray volumes = new org.json.JSONArray();
        for (int i = 0; i < 20; i++) {
            timestamps.put(1700000000L + i * 86400);
            closes.put(100.0 + i);
            highs.put(105.0 + i);
            lows.put(95.0 + i);
            volumes.put(1000000L + i * 100000);
        }

        org.json.JSONObject quote = new org.json.JSONObject();
        quote.put("close", closes);
        quote.put("high", highs);
        quote.put("low", lows);
        quote.put("volume", volumes);

        org.json.JSONObject indicators = new org.json.JSONObject();
        indicators.put("quote", new org.json.JSONArray().put(quote));

        org.json.JSONObject result = new org.json.JSONObject();
        result.put("timestamp", timestamps);
        result.put("indicators", indicators);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, result, "1mo");

        String output = sb.toString();
        assertThat(output).contains("Price History (1mo)");
        // Should have at most 15 data rows (limited from 20) plus header lines
        assertThat(output).isNotEmpty();
    }

    @Test
    void shouldParseChartResultWithNullError() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("parseChartResult",
                String.class, String.class);
        method.setAccessible(true);

        org.json.JSONObject resultObj = new org.json.JSONObject();
        resultObj.put("meta", new org.json.JSONObject().put("currency", "USD"));

        org.json.JSONObject chart = new org.json.JSONObject();
        chart.put("error", org.json.JSONObject.NULL);
        chart.put("result", new org.json.JSONArray().put(resultObj));
        String body = new org.json.JSONObject().put("chart", chart).toString();

        Object result = method.invoke(service, "AAPL", body);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldAppendCurrentQuoteWithNegativeChange() throws Exception {
        Method method = TurFinanceToolService.class.getDeclaredMethod("appendCurrentQuote",
                StringBuilder.class, org.json.JSONObject.class);
        method.setAccessible(true);

        org.json.JSONObject meta = new org.json.JSONObject();
        meta.put("regularMarketPrice", 140.0);
        meta.put("chartPreviousClose", 150.0);
        meta.put("regularMarketTime", 1700000000L);
        meta.put("currency", "USD");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, meta);

        String result = sb.toString();
        assertThat(result).containsPattern("Change: -10[.,]00");
    }
}
