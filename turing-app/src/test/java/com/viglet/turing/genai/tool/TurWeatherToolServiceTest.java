package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TurWeatherToolServiceTest {

    private TurWeatherToolService service;

    @BeforeEach
    void setUp() {
        service = new TurWeatherToolService();
    }

    @Test
    void shouldBuildLocationStringWithAllParts() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "buildLocationString", String.class, String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "São Paulo", "São Paulo", "Brazil");

        assertThat(result).isEqualTo("São Paulo, São Paulo, Brazil");
    }

    @Test
    void shouldBuildLocationStringWithoutAdmin() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "buildLocationString", String.class, String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "London", "", "United Kingdom");

        assertThat(result).isEqualTo("London, United Kingdom");
    }

    @Test
    void shouldBuildLocationStringWithNameOnly() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "buildLocationString", String.class, String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "Tokyo", "", "");

        assertThat(result).isEqualTo("Tokyo");
    }

    @Test
    void shouldFormatCurrentConditions() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatCurrentConditions", StringBuilder.class, JSONObject.class);
        method.setAccessible(true);

        JSONObject weatherJson = new JSONObject();
        JSONObject current = new JSONObject();
        current.put("temperature_2m", 25.5);
        current.put("apparent_temperature", 27.0);
        current.put("relative_humidity_2m", 65);
        current.put("wind_speed_10m", 12.3);
        current.put("weather_code", 0);
        weatherJson.put("current", current);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, weatherJson);

        String result = sb.toString();
        assertThat(result)
                .contains("Current Conditions")
                .contains("Temperature: 25.5°C")
                .contains("Feels like: 27.0°C")
                .contains("Humidity: 65%")
                .contains("Wind: 12.3 km/h")
                .contains("Clear sky");
    }

    @Test
    void shouldFormatCurrentConditionsWithUnknownWeatherCode() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatCurrentConditions", StringBuilder.class, JSONObject.class);
        method.setAccessible(true);

        JSONObject weatherJson = new JSONObject();
        JSONObject current = new JSONObject();
        current.put("temperature_2m", 20.0);
        current.put("apparent_temperature", 19.0);
        current.put("relative_humidity_2m", 50);
        current.put("wind_speed_10m", 5.0);
        current.put("weather_code", 999);
        weatherJson.put("current", current);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, weatherJson);

        assertThat(sb.toString()).contains("Unknown (999)");
    }

    @Test
    void shouldSkipCurrentConditionsWhenNotPresent() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatCurrentConditions", StringBuilder.class, JSONObject.class);
        method.setAccessible(true);

        JSONObject weatherJson = new JSONObject();
        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, weatherJson);

        assertThat(sb).isEmpty();
    }

    @Test
    void shouldSkipDailyForecastWhenNotPresent() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatDailyForecast", StringBuilder.class, JSONObject.class, int.class);
        method.setAccessible(true);

        JSONObject weatherJson = new JSONObject();
        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, weatherJson, 3);

        assertThat(sb).isEmpty();
    }

    @Test
    void shouldFormatDailyForecast() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatDailyForecast", StringBuilder.class, JSONObject.class, int.class);
        method.setAccessible(true);

        JSONObject weatherJson = new JSONObject();
        JSONObject daily = new JSONObject();
        daily.put("time", new org.json.JSONArray("[\"2026-03-08\"]"));
        daily.put("temperature_2m_min", new org.json.JSONArray("[15.0]"));
        daily.put("temperature_2m_max", new org.json.JSONArray("[28.0]"));
        daily.put("weather_code", new org.json.JSONArray("[61]"));
        daily.put("precipitation_probability_max", new org.json.JSONArray("[70]"));
        daily.put("wind_speed_10m_max", new org.json.JSONArray("[20.5]"));
        weatherJson.put("daily", daily);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, weatherJson, 1);

        String result = sb.toString();
        assertThat(result)
                .contains("1-Day Forecast")
                .contains("2026-03-08")
                .contains("15.0°C")
                .contains("28.0°C")
                .contains("Slight rain")
                .contains("Rain: 70%")
                .contains("Wind: 20.5 km/h");
    }

    @Test
    void shouldFormatWeatherResponse() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatWeatherResponse", String.class, String.class, String.class,
                double.class, double.class, JSONObject.class, int.class);
        method.setAccessible(true);

        JSONObject weatherJson = new JSONObject();
        StringBuilder result = (StringBuilder) method.invoke(service,
                "Paris", "Île-de-France", "France", 48.85, 2.35, weatherJson, 3);

        assertThat(result.toString())
                .contains("Weather for: Paris, Île-de-France, France")
                .contains("Coordinates: 48.85, 2.35");
    }
}
