package com.viglet.turing.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.system.TurGlobalDecimalSeparator;
import com.viglet.turing.system.TurGlobalSettingsService;

@ExtendWith(MockitoExtension.class)
class TurDecimalFieldNormalizerTest {

    @Mock
    private TurGlobalSettingsService turGlobalSettingsService;

    private TurDecimalFieldNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new TurDecimalFieldNormalizer(turGlobalSettingsService);
    }

    @Test
    void shouldIdentifyDecimalFieldTypes() {
        assertThat(normalizer.isDecimalFieldType(TurSEFieldType.FLOAT)).isTrue();
        assertThat(normalizer.isDecimalFieldType(TurSEFieldType.DOUBLE)).isTrue();
        assertThat(normalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).isTrue();
        assertThat(normalizer.isDecimalFieldType(TurSEFieldType.STRING)).isFalse();
        assertThat(normalizer.isDecimalFieldType(null)).isFalse();
    }

    @Test
    void shouldNormalizeCanonicalDecimalForCommaLocale() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.COMMA);

        assertThat(normalizer.normalizeCanonicalDecimal(" R$ 1.234,56 ")).contains("1234.56");
        assertThat(normalizer.normalizeCanonicalDecimal("1,234.56")).isEmpty();
    }

    @Test
    void shouldNormalizeCanonicalDecimalForDotLocale() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.DOT);

        assertThat(normalizer.normalizeCanonicalDecimal("$ 1,234.56")).contains("1234.56");
        assertThat(normalizer.normalizeCanonicalDecimal("1.234,56")).isEmpty();
    }

    @Test
    void shouldKeepNumericInputAsCanonicalString() {
        assertThat(normalizer.normalizeCanonicalDecimal(1234.5)).contains("1234.5");
    }

    @Test
    void shouldNormalizeNumericValueForFloatAndDouble() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.DOT);

        assertThat(normalizer.normalizeNumericValue(TurSEFieldType.FLOAT, "1,234.50"))
                .hasValueSatisfying(value -> {
                    assertThat(value).isInstanceOf(Float.class);
                    assertThat(((Float) value).doubleValue()).isEqualTo(1234.5d);
                });

        assertThat(normalizer.normalizeNumericValue(TurSEFieldType.DOUBLE, "1,234.50"))
                .hasValueSatisfying(value -> {
                    assertThat(value).isInstanceOf(Double.class);
                    assertThat((Double) value).isEqualTo(1234.5d);
                });
    }

    @Test
    void shouldNormalizeNumericValueForCurrencyAsDouble() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.COMMA);

        assertThat(normalizer.normalizeNumericValue(TurSEFieldType.CURRENCY, "1.234,50"))
                .contains(1234.5d);
    }

    @Test
    void shouldReturnEmptyForInvalidOrUnsupportedInput() {
        assertThat(normalizer.normalizeNumericValue(TurSEFieldType.STRING, "10.00")).isEmpty();
        assertThat(normalizer.normalizeNumericValue(TurSEFieldType.DOUBLE, "abc")).isEmpty();
        assertThat(normalizer.normalizeNumericValue(TurSEFieldType.DOUBLE, null)).isEmpty();
        assertThat(normalizer.normalizeNumericValue(null, "10.00")).isEmpty();
        assertThat(normalizer.normalizeCanonicalDecimal("   ")).isEmpty();
        assertThat(normalizer.normalizeCanonicalDecimal(null)).isEmpty();
    }
}
