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

    @Test
    void shouldFormatWeatherResponseWithCurrentAndDaily() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatWeatherResponse", String.class, String.class, String.class,
                double.class, double.class, JSONObject.class, int.class);
        method.setAccessible(true);

        JSONObject weatherJson = new JSONObject();

        JSONObject current = new JSONObject();
        current.put("temperature_2m", 22.0);
        current.put("apparent_temperature", 21.0);
        current.put("relative_humidity_2m", 55);
        current.put("wind_speed_10m", 8.0);
        current.put("weather_code", 2);
        weatherJson.put("current", current);

        JSONObject daily = new JSONObject();
        daily.put("time", new org.json.JSONArray("[\"2026-03-26\"]"));
        daily.put("temperature_2m_min", new org.json.JSONArray("[18.0]"));
        daily.put("temperature_2m_max", new org.json.JSONArray("[25.0]"));
        daily.put("weather_code", new org.json.JSONArray("[2]"));
        weatherJson.put("daily", daily);

        StringBuilder result = (StringBuilder) method.invoke(service,
                "Berlin", "Berlin", "Germany", 52.52, 13.41, weatherJson, 1);

        String output = result.toString();
        assertThat(output)
                .contains("Weather for: Berlin, Berlin, Germany")
                .contains("Current Conditions")
                .contains("1-Day Forecast");
    }

    @Test
    void shouldBuildLocationStringWithCountryOnly() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "buildLocationString", String.class, String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "Monaco", "", "Monaco");

        assertThat(result).isEqualTo("Monaco, Monaco");
    }

    @Test
    void shouldBuildLocationStringWithAdminOnly() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "buildLocationString", String.class, String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "City", "State", "");

        assertThat(result).isEqualTo("City, State");
    }

    @Test
    void shouldFormatForecastDayWithoutPrecipAndWind() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatForecastDay", StringBuilder.class, int.class, JSONObject.class);
        method.setAccessible(true);

        JSONObject daily = new JSONObject();
        daily.put("time", new org.json.JSONArray("[\"2026-03-26\"]"));
        daily.put("temperature_2m_min", new org.json.JSONArray("[10.0]"));
        daily.put("temperature_2m_max", new org.json.JSONArray("[20.0]"));
        daily.put("weather_code", new org.json.JSONArray("[0]"));
        // No precipitation_probability_max or wind_speed_10m_max

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, 0, daily);

        String result = sb.toString();
        assertThat(result)
                .contains("2026-03-26")
                .contains("Clear sky")
                .doesNotContain("Rain:")
                .doesNotContain("Wind:");
    }

    @Test
    void shouldFormatForecastDayWithNullPrecipValue() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatForecastDay", StringBuilder.class, int.class, JSONObject.class);
        method.setAccessible(true);

        JSONObject daily = new JSONObject();
        daily.put("time", new org.json.JSONArray("[\"2026-03-26\"]"));
        daily.put("temperature_2m_min", new org.json.JSONArray("[10.0]"));
        daily.put("temperature_2m_max", new org.json.JSONArray("[20.0]"));
        daily.put("weather_code", new org.json.JSONArray("[3]"));
        org.json.JSONArray precipArray = new org.json.JSONArray();
        precipArray.put(org.json.JSONObject.NULL);
        daily.put("precipitation_probability_max", precipArray);
        org.json.JSONArray windArray = new org.json.JSONArray();
        windArray.put(org.json.JSONObject.NULL);
        daily.put("wind_speed_10m_max", windArray);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, 0, daily);

        String result = sb.toString();
        assertThat(result)
                .contains("Overcast")
                .doesNotContain("Rain:")
                .doesNotContain("Wind:");
    }

    @Test
    void shouldFormatMultipleForecastDays() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatDailyForecast", StringBuilder.class, JSONObject.class, int.class);
        method.setAccessible(true);

        JSONObject weatherJson = new JSONObject();
        JSONObject daily = new JSONObject();
        daily.put("time", new org.json.JSONArray("[\"2026-03-26\",\"2026-03-27\",\"2026-03-28\"]"));
        daily.put("temperature_2m_min", new org.json.JSONArray("[10.0,12.0,14.0]"));
        daily.put("temperature_2m_max", new org.json.JSONArray("[20.0,22.0,24.0]"));
        daily.put("weather_code", new org.json.JSONArray("[0,1,2]"));
        weatherJson.put("daily", daily);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, weatherJson, 3);

        String result = sb.toString();
        assertThat(result)
                .contains("3-Day Forecast")
                .contains("2026-03-26")
                .contains("2026-03-27")
                .contains("2026-03-28")
                .contains("Clear sky")
                .contains("Mainly clear")
                .contains("Partly cloudy");
    }

    @Test
    void getWeatherShouldClampDaysToValidRange() {
        // days < 1 or > 7 should be adjusted to 3
        // Since this calls external APIs, we just verify it doesn't crash with bad args
        // The actual HTTP call will fail, and we verify the error handling
        String result = service.getWeather("NonExistentPlace99999", 0);
        // Either location not found or network error
        assertThat(result).isNotBlank();

        result = service.getWeather("NonExistentPlace99999", 10);
        assertThat(result).isNotBlank();
    }

    @Test
    void shouldFormatCurrentConditionsWithAllWeatherCodes() throws Exception {
        Method method = TurWeatherToolService.class.getDeclaredMethod(
                "formatCurrentConditions", StringBuilder.class, JSONObject.class);
        method.setAccessible(true);

        int[] testCodes = {0, 1, 2, 3, 45, 48, 51, 53, 55, 61, 63, 65, 71, 73, 75, 80, 81, 82, 95, 96, 99};
        String[] expectedDescriptions = {
                "Clear sky", "Mainly clear", "Partly cloudy", "Overcast",
                "Fog", "Depositing rime fog",
                "Light drizzle", "Moderate drizzle", "Dense drizzle",
                "Slight rain", "Moderate rain", "Heavy rain",
                "Slight snow", "Moderate snow", "Heavy snow",
                "Slight rain showers", "Moderate rain showers", "Violent rain showers",
                "Thunderstorm", "Thunderstorm with slight hail", "Thunderstorm with heavy hail"
        };

        for (int i = 0; i < testCodes.length; i++) {
            JSONObject weatherJson = new JSONObject();
            JSONObject current = new JSONObject();
            current.put("temperature_2m", 20.0);
            current.put("apparent_temperature", 19.0);
            current.put("relative_humidity_2m", 50);
            current.put("wind_speed_10m", 5.0);
            current.put("weather_code", testCodes[i]);
            weatherJson.put("current", current);

            StringBuilder sb = new StringBuilder();
            method.invoke(service, sb, weatherJson);

            assertThat(sb.toString()).contains(expectedDescriptions[i]);
        }
    }
}
