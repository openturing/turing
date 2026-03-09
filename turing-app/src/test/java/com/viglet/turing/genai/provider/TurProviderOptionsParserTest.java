package com.viglet.turing.genai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TurProviderOptionsParserTest {

    private TurProviderOptionsParser parser;

    @BeforeEach
    void setUp() {
        parser = new TurProviderOptionsParser();
    }

    @Test
    void testParse() {
        String json = "{\"key\":\"value\"}";
        Map<String, Object> map = parser.parse(json);
        assertEquals("value", map.get("key"));

        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("invalid json").isEmpty());
    }

    @Test
    void testStringValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("str", " test ");
        map.put("empty", " ");

        assertEquals("test", parser.stringValue(map, "str"));
        assertNull(parser.stringValue(map, "missing"));
        assertNull(parser.stringValue(map, "empty"));
    }

    @Test
    void testIntValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("int", 10);
        map.put("strInt", " 20 ");
        map.put("invalid", "abc");

        assertEquals(10, parser.intValue(map, "int"));
        assertEquals(20, parser.intValue(map, "strInt"));
        assertNull(parser.intValue(map, "invalid"));
        assertNull(parser.intValue(map, "missing"));
    }

    @Test
    void testDoubleValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("double", 10.5);
        map.put("strDouble", " 20.5 ");
        map.put("invalid", "abc");

        assertEquals(10.5, parser.doubleValue(map, "double"));
        assertEquals(20.5, parser.doubleValue(map, "strDouble"));
        assertNull(parser.doubleValue(map, "invalid"));
    }

    @Test
    void testBooleanValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("bool", true);
        map.put("strBool", " true ");

        assertEquals(true, parser.booleanValue(map, "bool"));
        assertEquals(true, parser.booleanValue(map, "strBool"));
        assertNull(parser.booleanValue(map, "missing"));
    }

    @Test
    void testStringListValue() {
        Map<String, Object> map = new HashMap<>();
        map.put("list", List.of("a", "b "));
        map.put("str", "a, b,c");
        map.put("empty", "");

        assertEquals(List.of("a", "b"), parser.stringListValue(map, "list"));
        assertEquals(List.of("a", "b", "c"), parser.stringListValue(map, "str"));
        assertTrue(parser.stringListValue(map, "empty").isEmpty());
        assertTrue(parser.stringListValue(map, "missing").isEmpty());
    }
}
