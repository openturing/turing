package com.viglet.turing.solr;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.system.TurGlobalDecimalSeparator;
import com.viglet.turing.system.TurGlobalSettingsService;

@Component
public class TurDecimalFieldNormalizer {
    private static final Pattern COMMA_DECIMAL_PATTERN = Pattern
            .compile("^[+-]?(?:\\d{1,3}(?:\\.\\d{3})*|\\d+)(?:,\\d+)?$");
    private static final Pattern DOT_DECIMAL_PATTERN = Pattern
            .compile("^[+-]?(?:\\d{1,3}(?:,\\d{3})*|\\d+)(?:\\.\\d+)?$");

    private final TurGlobalSettingsService turGlobalSettingsService;

    public TurDecimalFieldNormalizer(TurGlobalSettingsService turGlobalSettingsService) {
        this.turGlobalSettingsService = turGlobalSettingsService;
    }

    public boolean isDecimalFieldType(TurSEFieldType fieldType) {
        return fieldType == TurSEFieldType.FLOAT
                || fieldType == TurSEFieldType.DOUBLE
                || fieldType == TurSEFieldType.CURRENCY;
    }

    public Optional<Object> normalizeNumericValue(TurSEFieldType fieldType, Object rawValue) {
        if (rawValue == null || fieldType == null || !isDecimalFieldType(fieldType)) {
            return Optional.empty();
        }

        Optional<String> canonicalDecimal = normalizeCanonicalDecimal(rawValue);
        if (canonicalDecimal.isEmpty()) {
            return Optional.empty();
        }

        Optional<Double> parsedValue = parseStrictDecimal(canonicalDecimal.get());
        if (parsedValue.isEmpty()) {
            return Optional.empty();
        }

        if (fieldType == TurSEFieldType.FLOAT) {
            return Optional.of(parsedValue.get().floatValue());
        }

        return Optional.of(parsedValue.get());
    }

    public Optional<String> normalizeCanonicalDecimal(Object rawValue) {
        if (rawValue == null) {
            return Optional.empty();
        }

        if (rawValue instanceof Number number) {
            return Optional.of(number.toString());
        }

        String normalizedInput = normalizeInput(rawValue.toString());
        if (normalizedInput.isEmpty()) {
            return Optional.empty();
        }

        TurGlobalDecimalSeparator decimalSeparator = turGlobalSettingsService.getDecimalSeparator();
        if (decimalSeparator == TurGlobalDecimalSeparator.COMMA) {
            if (!COMMA_DECIMAL_PATTERN.matcher(normalizedInput).matches()) {
                return Optional.empty();
            }
            return Optional.of(normalizedInput.replace(".", "").replace(',', '.'));
        }

        if (!DOT_DECIMAL_PATTERN.matcher(normalizedInput).matches()) {
            return Optional.empty();
        }
        return Optional.of(normalizedInput.replace(",", ""));
    }

    private Optional<Double> parseStrictDecimal(String canonical) {
        try {
            return Optional.of(Double.parseDouble(canonical));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String normalizeInput(String value) {
        return value == null
                ? ""
                : value
                        .trim()
                        .replace("\u00A0", "")
                        .replace(" ", "")
                        .replaceAll("[^0-9,\\.\\-+]", "")
                        .toUpperCase(Locale.ROOT);
    }
}
