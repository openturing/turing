package com.viglet.turing.genai.tool;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurFinanceToolService {

    private static final String TIMESTAMP = "timestamp";
    private static final String ERROR_MESSAGE = "error";
    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String YAHOO_SEARCH_URL = "https://query1.finance.yahoo.com/v1/finance/search";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool(name = "get_stock_quote", description = ".")
    public String getStockQuote(String symbol, String range) {
        log.info("[Finance Tool] get_stock_quote called: symbol={}, range={}", symbol, range);
        range = normalizeRange(range);

        try {
            HttpResponse<String> response = fetchChartData(symbol, range);
            if (response.statusCode() != 200) {
                return formatHttpError(symbol, response.statusCode());
            }

            JSONObject result = parseChartResult(symbol, response.body());
            if (result == null) {
                return "No data found for symbol: " + symbol;
            }

            return buildQuoteOutput(symbol, result, range);

        } catch (IOException e) {
            log.error("[Finance Tool] get_stock_quote failed for {}: {}", symbol, e.getMessage(), e);
            return "Error fetching stock data for " + symbol + ": " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Finance Tool] get_stock_quote failed for {}: {}", symbol, e.getMessage(), e);
            return "Error fetching stock data for " + symbol + ": " + e.getMessage();
        }
    }

    private String normalizeRange(String range) {
        return (range == null || range.isBlank()) ? "5d" : range;
    }

    private HttpResponse<String> fetchChartData(String symbol, String range) throws IOException, InterruptedException {
        String interval = mapRangeToInterval(range);
        String url = YAHOO_CHART_URL
                + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
                + "?interval=" + interval
                + "&range=" + range;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("User-Agent", "Mozilla/5.0 (compatible; TuringBot/1.0)")
                .GET()
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String mapRangeToInterval(String range) {
        return switch (range) {
            case "1d" -> "5m";
            case "5d" -> "1d";
            case "1mo" -> "1d";
            case "3mo" -> "1wk";
            case "6mo" -> "1wk";
            case "1y" -> "1mo";
            default -> "1d";
        };
    }

    private String formatHttpError(String symbol, int statusCode) {
        return "Error fetching data for " + symbol + ": HTTP " + statusCode
                + ". Make sure the ticker symbol is correct.";
    }

    private JSONObject parseChartResult(String symbol, String responseBody) {
        JSONObject json = new JSONObject(responseBody);
        JSONObject chart = json.getJSONObject("chart");

        if (chart.has(ERROR_MESSAGE) && !chart.isNull(ERROR_MESSAGE)) {
            String errorMsg = chart.getJSONObject(ERROR_MESSAGE).optString("description", "Unknown error");
            throw new IllegalArgumentException("Error for symbol " + symbol + ": " + errorMsg);
        }

        JSONArray results = chart.getJSONArray("result");
        return results.isEmpty() ? null : results.getJSONObject(0);
    }

    private String buildQuoteOutput(String symbol, JSONObject result, String range) {
        StringBuilder sb = new StringBuilder();
        JSONObject meta = result.getJSONObject("meta");

        appendHeaderInfo(sb, symbol, meta);
        appendCurrentQuote(sb, meta);
        appendHistoricalData(sb, result, range);

        String output = sb.toString();
        log.info("[Finance Tool] get_stock_quote: returned {} chars for {}", output.length(), symbol);
        return output;
    }

    private void appendHeaderInfo(StringBuilder sb, String symbol, JSONObject meta) {
        String shortName = meta.optString("shortName", symbol);
        String exchangeName = meta.optString("exchangeName", "");
        String currency = meta.optString("currency", "USD");

        sb.append("Symbol: ").append(symbol);
        if (!shortName.equals(symbol))
            sb.append(" (").append(shortName).append(")");
        sb.append("\n");
        if (!exchangeName.isEmpty())
            sb.append("Exchange: ").append(exchangeName).append("\n");
        sb.append("Currency: ").append(currency).append("\n");
    }

    private void appendCurrentQuote(StringBuilder sb, JSONObject meta) {
        double regularMarketPrice = meta.optDouble("regularMarketPrice", 0);
        double previousClose = meta.optDouble("chartPreviousClose", 0);
        double change = regularMarketPrice - previousClose;
        double changePct = previousClose > 0 ? (change / previousClose) * 100 : 0;
        long regularMarketTime = meta.optLong("regularMarketTime", 0);
        String currency = meta.optString("currency", "USD");

        sb.append("\n--- Current Quote ---\n");
        sb.append("Price: ").append(String.format("%.2f", regularMarketPrice)).append(" ").append(currency)
                .append("\n");
        sb.append("Previous Close: ").append(String.format("%.2f", previousClose)).append("\n");
        sb.append("Change: ").append(String.format("%+.2f", change))
                .append(" (").append(String.format("%+.2f%%", changePct)).append(")\n");

        if (regularMarketTime > 0) {
            sb.append("Last Update: ").append(DATE_FMT.format(Instant.ofEpochSecond(regularMarketTime)))
                    .append("\n");
        }
    }

    private void appendHistoricalData(StringBuilder sb, JSONObject result, String range) {
        if (!result.has(TIMESTAMP) || result.isNull(TIMESTAMP)) {
            return;
        }

        JSONArray timestamps = result.getJSONArray(TIMESTAMP);
        JSONObject indicators = result.getJSONObject("indicators");
        JSONObject quote = indicators.getJSONArray("quote").getJSONObject(0);
        JSONArray closes = quote.optJSONArray("close");
        JSONArray volumes = quote.optJSONArray("volume");
        JSONArray highs = quote.optJSONArray("high");
        JSONArray lows = quote.optJSONArray("low");

        sb.append("\n--- Price History (").append(range).append(") ---\n");
        sb.append("Date | Close | High | Low | Volume\n");

        int count = Math.min(timestamps.length(), 15);
        int start = Math.max(0, timestamps.length() - count);
        for (int i = start; i < timestamps.length(); i++) {
            appendHistoryRow(sb, timestamps, closes, highs, lows, volumes, i);
        }
    }

    private void appendHistoryRow(StringBuilder sb, JSONArray timestamps, JSONArray closes,
            JSONArray highs, JSONArray lows, JSONArray volumes, int i) {
        long ts = timestamps.getLong(i);
        String date = DATE_FMT.format(Instant.ofEpochSecond(ts));
        String close = formatValue(closes, i);
        String high = formatValue(highs, i);
        String low = formatValue(lows, i);
        String vol = volumes != null && !volumes.isNull(i) ? formatVolume(volumes.getLong(i)) : "N/A";

        sb.append(date).append(" | ").append(close).append(" | ")
                .append(high).append(" | ").append(low).append(" | ").append(vol).append("\n");
    }

    private String formatValue(JSONArray array, int index) {
        return array != null && !array.isNull(index)
                ? String.format("%.2f", array.getDouble(index))
                : "N/A";
    }

    @Tool(name = "search_ticker", description = ".")
    public String searchTicker(String query) {
        log.info("[Finance Tool] search_ticker called: query={}", query);
        try {
            String url = YAHOO_SEARCH_URL
                    + "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&quotesCount=8&newsCount=0";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "Mozilla/5.0 (compatible; TuringBot/1.0)")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());

            JSONArray quotes = json.optJSONArray("quotes");
            if (quotes == null || quotes.isEmpty()) {
                return "No results found for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Search results for '").append(query).append("':\n\n");
            sb.append("Symbol | Name | Exchange | Type\n");

            for (int i = 0; i < quotes.length(); i++) {
                JSONObject q = quotes.getJSONObject(i);
                String sym = q.optString("symbol", "");
                String name = q.optString("shortname", q.optString("longname", ""));
                String exchange = q.optString("exchDisp", "");
                String type = q.optString("quoteType", "");
                sb.append(sym).append(" | ").append(name).append(" | ")
                        .append(exchange).append(" | ").append(type).append("\n");
            }

            String result = sb.toString();
            log.info("[Finance Tool] search_ticker: found {} results for '{}'", quotes.length(), query);
            return result;

        } catch (IOException e) {
            log.error("[Finance Tool] search_ticker failed for {}: {}", query, e.getMessage(), e);
            return "Error searching for " + query + ": " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Finance Tool] search_ticker failed for {}: {}", query, e.getMessage(), e);
            return "Error searching for " + query + ": " + e.getMessage();
        }
    }

    private String formatVolume(long volume) {
        if (volume >= 1_000_000_000)
            return String.format("%.1fB", volume / 1_000_000_000.0);
        if (volume >= 1_000_000)
            return String.format("%.1fM", volume / 1_000_000.0);
        if (volume >= 1_000)
            return String.format("%.1fK", volume / 1_000.0);
        return String.valueOf(volume);
    }
}
