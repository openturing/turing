package com.viglet.turing.solr;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.system.TurGlobalDecimalSeparator;
import com.viglet.turing.system.TurGlobalSettingsService;

@ExtendWith(MockitoExtension.class)
class TurDecimalFieldNormalizerTest {

    @Mock private TurGlobalSettingsService turGlobalSettingsService;
    @InjectMocks private TurDecimalFieldNormalizer normalizer;

    @Test
    void isDecimalFieldTypeShouldReturnTrueForFloat() {
        assertTrue(normalizer.isDecimalFieldType(TurSEFieldType.FLOAT));
    }

    @Test
    void isDecimalFieldTypeShouldReturnTrueForDouble() {
        assertTrue(normalizer.isDecimalFieldType(TurSEFieldType.DOUBLE));
    }

    @Test
    void isDecimalFieldTypeShouldReturnTrueForCurrency() {
        assertTrue(normalizer.isDecimalFieldType(TurSEFieldType.CURRENCY));
    }

    @Test
    void isDecimalFieldTypeShouldReturnFalseForString() {
        assertFalse(normalizer.isDecimalFieldType(TurSEFieldType.STRING));
    }

    @Test
    void isDecimalFieldTypeShouldReturnFalseForInt() {
        assertFalse(normalizer.isDecimalFieldType(TurSEFieldType.INT));
    }

    @Test
    void normalizeNumericValueShouldReturnEmptyForNull() {
        assertEquals(Optional.empty(), normalizer.normalizeNumericValue(TurSEFieldType.FLOAT, null));
    }

    @Test
    void normalizeNumericValueShouldReturnEmptyForNonDecimalType() {
        assertEquals(Optional.empty(), normalizer.normalizeNumericValue(TurSEFieldType.STRING, "1.5"));
    }

    @Test
    void normalizeNumericValueDotSeparatorShouldParseSimpleDecimal() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.DOT);
        Optional<Object> result = normalizer.normalizeNumericValue(TurSEFieldType.DOUBLE, "1.5");
        assertTrue(result.isPresent());
        assertEquals(1.5, result.get());
    }

    @Test
    void normalizeNumericValueDotSeparatorShouldReturnFloatForFloatType() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.DOT);
        Optional<Object> result = normalizer.normalizeNumericValue(TurSEFieldType.FLOAT, "1.5");
        assertTrue(result.isPresent());
        assertInstanceOf(Float.class, result.get());
        assertEquals(1.5f, (Float) result.get(), 0.001f);
    }

    @Test
    void normalizeNumericValueCommaSeparatorShouldNormalize() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.COMMA);
        Optional<Object> result = normalizer.normalizeNumericValue(TurSEFieldType.DOUBLE, "1.500,75");
        assertTrue(result.isPresent());
        assertEquals(1500.75, result.get());
    }

    @Test
    void normalizeCanonicalDecimalShouldReturnEmptyForNull() {
        assertEquals(Optional.empty(), normalizer.normalizeCanonicalDecimal(null));
    }

    @Test
    void normalizeCanonicalDecimalShouldReturnNumberToStringForNumber() {
        Optional<String> result = normalizer.normalizeCanonicalDecimal(42.5);
        assertTrue(result.isPresent());
        assertEquals("42.5", result.get());
    }

    @Test
    void normalizeCanonicalDecimalDotShouldRemoveThousandCommas() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.DOT);
        Optional<String> result = normalizer.normalizeCanonicalDecimal("1,500.75");
        assertTrue(result.isPresent());
        assertEquals("1500.75", result.get());
    }

    @Test
    void normalizeCanonicalDecimalCommaShouldConvertToCanonical() {
        when(turGlobalSettingsService.getDecimalSeparator()).thenReturn(TurGlobalDecimalSeparator.COMMA);
        Optional<String> result = normalizer.normalizeCanonicalDecimal("1.500,75");
        assertTrue(result.isPresent());
        assertEquals("1500.75", result.get());
    }

    @Test
    void normalizeCanonicalDecimalShouldReturnEmptyForNonNumeric() {
        // normalizeInput("abc") strips all non-numeric chars -> empty string -> Optional.empty()
        Optional<String> result = normalizer.normalizeCanonicalDecimal("abc");
        assertTrue(result.isEmpty());
    }

    @Test
    void normalizeNumericValueShouldHandleNumberInput() {
        Optional<Object> result = normalizer.normalizeNumericValue(TurSEFieldType.DOUBLE, 42.5);
        assertTrue(result.isPresent());
        assertEquals(42.5, result.get());
    }
}
