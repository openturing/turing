package com.viglet.turing.genai.provider;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TurProviderOptionsParser {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> parse(String providerOptionsJson) {
        if (!StringUtils.hasText(providerOptionsJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(providerOptionsJson, MAP_TYPE);
        } catch (Exception e) {
            log.warn("Ignoring invalid providerOptionsJson. Reason: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    public String stringValue(Map<String, Object> options, String key) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) {
            return null;
        }
        String value = String.valueOf(options.get(key)).trim();
        return StringUtils.hasText(value) ? value : null;
    }

    public Integer intValue(Map<String, Object> options, String key) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) {
            return null;
        }
        Object value = options.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public Double doubleValue(Map<String, Object> options, String key) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) {
            return null;
        }
        Object value = options.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    public Boolean booleanValue(Map<String, Object> options, String key) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) {
            return null;
        }
        Object value = options.get(key);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }

    public List<String> stringListValue(Map<String, Object> options, String key) {
        if (options == null || !options.containsKey(key) || options.get(key) == null) {
            return List.of();
        }
        Object value = options.get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(item -> item != null && StringUtils.hasText(String.valueOf(item)))
                    .map(item -> String.valueOf(item).trim())
                    .toList();
        }
        String raw = String.valueOf(value);
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return List.of(raw.split(",")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
