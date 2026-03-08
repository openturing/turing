package com.viglet.turing.genai.tool;

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

    private static final String YAHOO_CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String YAHOO_SEARCH_URL = "https://query1.finance.yahoo.com/v1/finance/search";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool(name = "get_stock_quote", description = """
            Gets the current stock price and market data for a given ticker symbol.
            Use this tool when the user asks about stock prices, market data, or financial quotes.
            Args:
                symbol (str): Stock ticker symbol (e.g., 'AAPL' for Apple, 'GOOGL' for Google, \
            'PETR4.SA' for Petrobras, '^BVSP' for Bovespa index, '^GSPC' for S&P 500, \
            '^DJI' for Dow Jones, 'BTC-USD' for Bitcoin, 'BRL=X' for USD/BRL exchange rate). Required.
                range (str): Time range for historical data. Options: '1d', '5d', '1mo', '3mo', '6mo', \
            '1y'. Default: '5d'.
            Returns:
                Current price, change, volume, and recent price history.""")
    public String getStockQuote(String symbol, String range) {
        log.info("[Finance Tool] get_stock_quote called: symbol={}, range={}", symbol, range);
        if (range == null || range.isBlank())
            range = "5d";

        try {
            String interval = switch (range) {
                case "1d" -> "5m";
                case "5d" -> "1d";
                case "1mo" -> "1d";
                case "3mo" -> "1wk";
                case "6mo" -> "1wk";
                case "1y" -> "1mo";
                default -> "1d";
            };

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

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return "Error fetching data for " + symbol + ": HTTP " + response.statusCode()
                        + ". Make sure the ticker symbol is correct.";
            }

            JSONObject json = new JSONObject(response.body());
            JSONObject chart = json.getJSONObject("chart");

            if (chart.has("error") && !chart.isNull("error")) {
                String errorMsg = chart.getJSONObject("error").optString("description", "Unknown error");
                return "Error for symbol " + symbol + ": " + errorMsg;
            }

            JSONArray results = chart.getJSONArray("result");
            if (results.isEmpty()) {
                return "No data found for symbol: " + symbol;
            }

            JSONObject result = results.getJSONObject(0);
            JSONObject meta = result.getJSONObject("meta");

            String currency = meta.optString("currency", "USD");
            String exchangeName = meta.optString("exchangeName", "");
            String shortName = meta.optString("shortName", symbol);
            double regularMarketPrice = meta.optDouble("regularMarketPrice", 0);
            double previousClose = meta.optDouble("chartPreviousClose", 0);
            double change = regularMarketPrice - previousClose;
            double changePct = previousClose > 0 ? (change / previousClose) * 100 : 0;

            StringBuilder sb = new StringBuilder();
            sb.append("Symbol: ").append(symbol);
            if (!shortName.equals(symbol))
                sb.append(" (").append(shortName).append(")");
            sb.append("\n");
            if (!exchangeName.isEmpty())
                sb.append("Exchange: ").append(exchangeName).append("\n");
            sb.append("Currency: ").append(currency).append("\n");
            sb.append("\n--- Current Quote ---\n");
            sb.append("Price: ").append(String.format("%.2f", regularMarketPrice)).append(" ").append(currency)
                    .append("\n");
            sb.append("Previous Close: ").append(String.format("%.2f", previousClose)).append("\n");
            sb.append("Change: ").append(String.format("%+.2f", change))
                    .append(" (").append(String.format("%+.2f%%", changePct)).append(")\n");

            // Market hours info
            long regularMarketTime = meta.optLong("regularMarketTime", 0);
            if (regularMarketTime > 0) {
                sb.append("Last Update: ").append(DATE_FMT.format(Instant.ofEpochSecond(regularMarketTime)))
                        .append("\n");
            }

            // Historical data
            if (result.has("timestamp") && !result.isNull("timestamp")) {
                JSONArray timestamps = result.getJSONArray("timestamp");
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
                    long ts = timestamps.getLong(i);
                    String date = DATE_FMT.format(Instant.ofEpochSecond(ts));
                    String close = closes != null && !closes.isNull(i)
                            ? String.format("%.2f", closes.getDouble(i))
                            : "N/A";
                    String high = highs != null && !highs.isNull(i)
                            ? String.format("%.2f", highs.getDouble(i))
                            : "N/A";
                    String low = lows != null && !lows.isNull(i)
                            ? String.format("%.2f", lows.getDouble(i))
                            : "N/A";
                    String vol = volumes != null && !volumes.isNull(i)
                            ? formatVolume(volumes.getLong(i))
                            : "N/A";
                    sb.append(date).append(" | ").append(close).append(" | ")
                            .append(high).append(" | ").append(low).append(" | ").append(vol).append("\n");
                }
            }

            String output = sb.toString();
            log.info("[Finance Tool] get_stock_quote: returned {} chars for {}", output.length(), symbol);
            return output;

        } catch (Exception e) {
            log.error("[Finance Tool] get_stock_quote failed for {}: {}", symbol, e.getMessage(), e);
            return "Error fetching stock data for " + symbol + ": " + e.getMessage();
        }
    }

    @Tool(name = "search_ticker", description = """
            Searches for a stock ticker symbol by company name or keyword.
            Use this tool when the user mentions a company name but not the ticker symbol, \
            or when you need to find the correct ticker for a stock, index, or cryptocurrency.
            Args:
                query (str): Company name or search keyword (e.g., 'Apple', 'Petrobras', 'Bitcoin'). Required.
            Returns:
                A list of matching ticker symbols with company name, exchange, and type.""")
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

        } catch (Exception e) {
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
