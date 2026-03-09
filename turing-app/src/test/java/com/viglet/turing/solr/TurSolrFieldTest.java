/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.viglet.turing.solr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurSolrField.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurSolrFieldTest {

    @Test
    void testConstructorThrowsException() {
        assertThatThrownBy(this::instantiateTurSolrField)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TurSolrField class");
    }

    private Object instantiateTurSolrField() throws Exception {
        return TurSolrField.class.getDeclaredConstructor().newInstance();
    }

    @Test
    void testConvertFieldToStringWithNull() {
        String result = TurSolrField.convertFieldToString(null);

        assertThat(result).isEqualTo(TurSolrField.EMPTY_STRING);
    }

    @Test
    void testConvertFieldToStringWithString() {
        String result = TurSolrField.convertFieldToString("test string");

        assertThat(result).isEqualTo("test string");
    }

    @Test
    void testConvertFieldToStringWithStringWithWhitespace() {
        String result = TurSolrField.convertFieldToString("  test string  ");

        assertThat(result).isEqualTo("test string");
    }

    @Test
    void testConvertFieldToStringWithEmptyString() {
        String result = TurSolrField.convertFieldToString("");

        assertThat(result).isEmpty();
    }

    @Test
    void testConvertFieldToStringWithLong() {
        String result = TurSolrField.convertFieldToString(12345L);

        assertThat(result).isEqualTo("12345");
    }

    @Test
    void testConvertFieldToStringWithNegativeLong() {
        String result = TurSolrField.convertFieldToString(-9876L);

        assertThat(result).isEqualTo("-9876");
    }

    @Test
    void testConvertFieldToStringWithZeroLong() {
        String result = TurSolrField.convertFieldToString(0L);

        assertThat(result).isEqualTo("0");
    }

    @Test
    void testConvertFieldToStringWithDate() {
        Date date = new Date(1704067200000L); // 2024-01-01 00:00:00 UTC
        SimpleDateFormat sdf = new SimpleDateFormat(TurSolrField.DATE_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone(TurSolrField.GMT));
        String expectedDate = sdf.format(date);

        String result = TurSolrField.convertFieldToString(date);

        assertThat(result).isEqualTo(expectedDate).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
    }

    @Test
    void testConvertFieldToStringWithArrayListOfStrings() {
        ArrayList<String> list = new ArrayList<>(Arrays.asList("first", "second", "third"));

        String result = TurSolrField.convertFieldToString(list);

        assertThat(result).isEqualTo("first");
    }

    @Test
    void testConvertFieldToStringWithArrayListOfStringsWithWhitespace() {
        ArrayList<String> list = new ArrayList<>(Arrays.asList("  first  ", "second"));

        String result = TurSolrField.convertFieldToString(list);

        assertThat(result).isEqualTo("first");
    }

    @Test
    void testConvertFieldToStringWithEmptyArrayList() {
        ArrayList<String> list = new ArrayList<>();

        String result = TurSolrField.convertFieldToString(list);

        assertThat(result).isEmpty();
    }

    @Test
    void testConvertFieldToStringWithArrayListOfLongs() {
        ArrayList<Long> list = new ArrayList<>(Arrays.asList(100L, 200L, 300L));

        String result = TurSolrField.convertFieldToString(list);

        assertThat(result).isEqualTo("100");
    }

    @Test
    void testConvertFieldToStringWithArrayListOfDates() {
        Date date = new Date(1704067200000L);
        ArrayList<Date> list = new ArrayList<>(Arrays.asList(date, new Date()));
        SimpleDateFormat sdf = new SimpleDateFormat(TurSolrField.DATE_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone(TurSolrField.GMT));
        String expectedDate = sdf.format(date);

        String result = TurSolrField.convertFieldToString(list);

        assertThat(result).isEqualTo(expectedDate);
    }

    @Test
    void testConvertFieldToStringWithArrayListWithNullElement() {
        ArrayList<String> list = new ArrayList<>();
        list.add(null);

        String result = TurSolrField.convertFieldToString(list);

        assertThat(result).isEmpty();
    }

    @Test
    void testConvertFieldToStringWithObjectArrayOfStrings() {
        Object[] array = new Object[] { "first", "second", "third" };

        String result = TurSolrField.convertFieldToString(array);

        assertThat(result).isEqualTo("first");
    }

    @Test
    void testConvertFieldToStringWithObjectArrayOfStringsWithWhitespace() {
        Object[] array = new Object[] { "  first  ", "second" };

        String result = TurSolrField.convertFieldToString(array);

        assertThat(result).isEqualTo("first");
    }

    @Test
    void testConvertFieldToStringWithObjectArrayOfLongs() {
        Object[] array = new Object[] { 999L, 888L };

        String result = TurSolrField.convertFieldToString(array);

        assertThat(result).isEqualTo("999");
    }

    @Test
    void testConvertFieldToStringWithObjectArrayOfDates() {
        Date date = new Date(1704067200000L);
        Object[] array = new Object[] { date, new Date() };

        String result = TurSolrField.convertFieldToString(array);
        assertThat(result).isNotEmpty().contains("2024");
    }

    @Test
    void testConvertFieldToStringWithNullObjectArray() {
        Object[] array = null;

        String result = TurSolrField.convertFieldToString(array);

        assertThat(result).isEmpty();
    }

    @Test
    void testConvertFieldToStringWithInteger() {
        Integer value = 42;

        String result = TurSolrField.convertFieldToString(value);

        assertThat(result).isEqualTo("42");
    }

    @Test
    void testConvertFieldToStringWithDouble() {
        Double value = 3.14159;

        String result = TurSolrField.convertFieldToString(value);

        assertThat(result).isEqualTo("3.14159");
    }

    @Test
    void testConvertFieldToStringWithBoolean() {
        Boolean value = true;

        String result = TurSolrField.convertFieldToString(value);

        assertThat(result).isEqualTo("true");
    }

    @Test
    void testConvertFieldToStringWithCustomObject() {
        Object value = new CustomObject("test");

        String result = TurSolrField.convertFieldToString(value);

        assertThat(result).isEqualTo("CustomObject: test");
    }

    @Test
    void testConvertFieldToStringWithCustomObjectWithWhitespace() {
        Object value = new CustomObject("  test  ");

        String result = TurSolrField.convertFieldToString(value);

        assertThat(result).isEqualTo("CustomObject:   test");
    }

    @Test
    void testConvertFieldToStringWithArrayListOfCustomObjects() {
        ArrayList<CustomObject> list = new ArrayList<>();
        list.add(new CustomObject("first"));
        list.add(new CustomObject("second"));

        String result = TurSolrField.convertFieldToString(list);

        assertThat(result).isEqualTo("CustomObject: first");
    }

    @Test
    void testDateFormatConstant() {
        assertThat(TurSolrField.DATE_FORMAT).isEqualTo("yyyy-MM-dd'T'HH:mm:ss'Z'");
    }

    @Test
    void testGmtConstant() {
        assertThat(TurSolrField.GMT).isEqualTo("GMT");
    }

    @Test
    void testEmptyStringConstant() {
        assertThat(TurSolrField.EMPTY_STRING).isEmpty();
    }

    @Test
    void testConvertFieldToStringWithSingleElementStringArray() {
        Object[] array = new Object[] { "single" };

        String result = TurSolrField.convertFieldToString(array);

        assertThat(result).isEqualTo("single");
    }

    @Test
    void testConvertFieldToStringWithSingleElementLongArray() {
        Object[] array = new Object[] { 777L };

        String result = TurSolrField.convertFieldToString(array);

        assertThat(result).isEqualTo("777");
    }

    @Test
    void testConvertFieldToStringDateFormatting() {
        Date date = new Date(0L); // Epoch time

        String result = TurSolrField.convertFieldToString(date);

        assertThat(result).isEqualTo("1970-01-01T00:00:00Z");
    }

    @Test
    void testConvertFieldToStringWithVeryLongString() {
        String longString = "a".repeat(1000);

        String result = TurSolrField.convertFieldToString("  " + longString + "  ");

        assertThat(result).hasSize(1000).isEqualTo(longString);
    }

    @Test
    void testConvertFieldToStringWithZeroInteger() {
        Integer value = 0;

        String result = TurSolrField.convertFieldToString(value);

        assertThat(result).isEqualTo("0");
    }

    @Test
    void testConvertFieldToStringWithNegativeInteger() {
        Integer value = -42;

        String result = TurSolrField.convertFieldToString(value);

        assertThat(result).isEqualTo("-42");
    }

    private static class CustomObject {
        private final String value;

        CustomObject(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "CustomObject: " + value;
        }
    }
}
