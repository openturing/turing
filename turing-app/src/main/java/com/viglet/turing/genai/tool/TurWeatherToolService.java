package com.viglet.turing.genai.tool;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurWeatherToolService {

    private static final String GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String RESULTS_KEY = "results";

    private static final Map<Integer, String> WMO_CODES = Map.ofEntries(
            Map.entry(0, "Clear sky"),
            Map.entry(1, "Mainly clear"),
            Map.entry(2, "Partly cloudy"),
            Map.entry(3, "Overcast"),
            Map.entry(45, "Fog"),
            Map.entry(48, "Depositing rime fog"),
            Map.entry(51, "Light drizzle"),
            Map.entry(53, "Moderate drizzle"),
            Map.entry(55, "Dense drizzle"),
            Map.entry(61, "Slight rain"),
            Map.entry(63, "Moderate rain"),
            Map.entry(65, "Heavy rain"),
            Map.entry(71, "Slight snow"),
            Map.entry(73, "Moderate snow"),
            Map.entry(75, "Heavy snow"),
            Map.entry(80, "Slight rain showers"),
            Map.entry(81, "Moderate rain showers"),
            Map.entry(82, "Violent rain showers"),
            Map.entry(95, "Thunderstorm"),
            Map.entry(96, "Thunderstorm with slight hail"),
            Map.entry(99, "Thunderstorm with heavy hail"));

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Tool(name = "get_weather", description = ".")
    public String getWeather(String location, int days) {
        log.info("[Weather Tool] get_weather called: location={}, days={}", location, days);
        if (days < 1 || days > 7)
            days = 3;

        try {
            JSONObject geoJson = fetchGeocoding(location);
            if (!geoJson.has(RESULTS_KEY) || geoJson.getJSONArray(RESULTS_KEY).isEmpty()) {
                return "Location not found: " + location;
            }

            JSONObject place = geoJson.getJSONArray(RESULTS_KEY).getJSONObject(0);
            double lat = place.getDouble("latitude");
            double lon = place.getDouble("longitude");
            String name = place.getString("name");
            String country = place.optString("country", "");
            String admin = place.optString("admin1", "");

            JSONObject weatherJson = fetchWeather(lat, lon, days);
            StringBuilder sb = formatWeatherResponse(name, admin, country, lat, lon, weatherJson, days);

            String result = sb.toString();
            log.info("[Weather Tool] get_weather: returned {} chars for {}", result.length(), location);
            return result;

        } catch (IOException e) {
            log.error("[Weather Tool] get_weather failed for {}: {}", location, e.getMessage(), e);
            return "Error fetching weather for " + location + ": " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Weather Tool] get_weather failed for {}: {}", location, e.getMessage(), e);
            return "Error fetching weather for " + location + ": " + e.getMessage();
        }
    }

    private JSONObject fetchGeocoding(String location) throws IOException, InterruptedException {
        String geoUrl = GEOCODING_URL + "?name="
                + URLEncoder.encode(location, StandardCharsets.UTF_8)
                + "&count=1&language=en&format=json";

        HttpRequest geoRequest = HttpRequest.newBuilder()
                .uri(URI.create(geoUrl))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> geoResponse = httpClient.send(geoRequest, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(geoResponse.body());
    }

    private JSONObject fetchWeather(double lat, double lon, int days) throws IOException, InterruptedException {
        String weatherUrl = FORECAST_URL
                + "?latitude=" + lat
                + "&longitude=" + lon
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,"
                + "weather_code,wind_speed_10m,wind_direction_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                + "precipitation_probability_max,wind_speed_10m_max"
                + "&timezone=auto"
                + "&forecast_days=" + days;

        HttpRequest weatherRequest = HttpRequest.newBuilder()
                .uri(URI.create(weatherUrl))
                .timeout(TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> weatherResponse = httpClient.send(weatherRequest,
                HttpResponse.BodyHandlers.ofString());
        return new JSONObject(weatherResponse.body());
    }

    private StringBuilder formatWeatherResponse(String name, String admin, String country, double lat, double lon,
            JSONObject weatherJson, int days) {
        StringBuilder sb = new StringBuilder();
        String fullLocation = buildLocationString(name, admin, country);
        sb.append("Weather for: ").append(fullLocation).append("\n");
        sb.append("Coordinates: ").append(lat).append(", ").append(lon).append("\n");

        formatCurrentConditions(sb, weatherJson);
        formatDailyForecast(sb, weatherJson, days);

        return sb;
    }

    private String buildLocationString(String name, String admin, String country) {
        StringBuilder location = new StringBuilder(name);
        if (!admin.isEmpty())
            location.append(", ").append(admin);
        if (!country.isEmpty())
            location.append(", ").append(country);
        return location.toString();
    }

    private void formatCurrentConditions(StringBuilder sb, JSONObject weatherJson) {
        if (weatherJson.has("current")) {
            JSONObject current = weatherJson.getJSONObject("current");
            int code = current.optInt("weather_code", -1);
            sb.append("\n--- Current Conditions ---\n");
            sb.append("Temperature: ").append(current.optDouble("temperature_2m", 0)).append("°C\n");
            sb.append("Feels like: ").append(current.optDouble("apparent_temperature", 0)).append("°C\n");
            sb.append("Humidity: ").append(current.optInt("relative_humidity_2m", 0)).append("%\n");
            sb.append("Wind: ").append(current.optDouble("wind_speed_10m", 0)).append(" km/h\n");
            sb.append("Condition: ").append(WMO_CODES.getOrDefault(code, "Unknown (" + code + ")")).append("\n");
        }
    }

    private void formatDailyForecast(StringBuilder sb, JSONObject weatherJson, int days) {
        if (weatherJson.has("daily")) {
            JSONObject daily = weatherJson.getJSONObject("daily");
            sb.append("\n--- ").append(days).append("-Day Forecast ---\n");
            JSONArray dates = daily.getJSONArray("time");
            for (int i = 0; i < dates.length(); i++) {
                formatForecastDay(sb, i, daily);
            }
        }
    }

    private void formatForecastDay(StringBuilder sb, int index, JSONObject daily) {
        JSONArray dates = daily.getJSONArray("time");
        JSONArray minTemps = daily.getJSONArray("temperature_2m_min");
        JSONArray maxTemps = daily.getJSONArray("temperature_2m_max");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray precipProb = daily.optJSONArray("precipitation_probability_max");
        JSONArray windMax = daily.optJSONArray("wind_speed_10m_max");

        int code = codes.optInt(index, -1);
        sb.append(dates.getString(index)).append(": ");
        sb.append(minTemps.optDouble(index, 0)).append("°C / ");
        sb.append(maxTemps.optDouble(index, 0)).append("°C");
        sb.append(" — ").append(WMO_CODES.getOrDefault(code, "Unknown"));
        if (precipProb != null && !precipProb.isNull(index)) {
            sb.append(" | Rain: ").append(precipProb.optInt(index, 0)).append("%");
        }
        if (windMax != null && !windMax.isNull(index)) {
            sb.append(" | Wind: ").append(windMax.optDouble(index, 0)).append(" km/h");
        }
        sb.append("\n");
    }
}
