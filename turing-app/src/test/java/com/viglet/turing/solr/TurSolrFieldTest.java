package com.viglet.turing.solr;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Date;

import org.junit.jupiter.api.Test;

class TurSolrFieldTest {

    @Test
    void convertFieldToStringShouldReturnEmptyForNull() {
        assertEquals("", TurSolrField.convertFieldToString(null));
    }

    @Test
    void convertFieldToStringShouldReturnTrimmedString() {
        assertEquals("hello", TurSolrField.convertFieldToString("  hello  "));
    }

    @Test
    void convertFieldToStringShouldHandleLong() {
        assertEquals("12345", TurSolrField.convertFieldToString(12345L));
    }

    @Test
    void convertFieldToStringShouldHandleDate() {
        Date date = new Date(0L); // epoch
        String result = TurSolrField.convertFieldToString(date);
        assertEquals("1970-01-01T00:00:00Z", result);
    }

    @Test
    void convertFieldToStringShouldHandleArrayList() {
        ArrayList<String> list = new ArrayList<>();
        list.add("  first  ");
        list.add("second");
        assertEquals("first", TurSolrField.convertFieldToString(list));
    }

    @Test
    void convertFieldToStringShouldReturnEmptyForEmptyArrayList() {
        assertEquals("", TurSolrField.convertFieldToString(new ArrayList<>()));
    }

    @Test
    void convertFieldToStringShouldHandleObjectArray() {
        Object[] arr = new Object[]{"  value  "};
        assertEquals("value", TurSolrField.convertFieldToString(arr));
    }

    @Test
    void convertFieldToStringShouldHandleLongInObjectArray() {
        Object[] arr = new Object[]{42L};
        assertEquals("42", TurSolrField.convertFieldToString(arr));
    }

    @Test
    void convertFieldToStringShouldHandleInteger() {
        assertEquals("42", TurSolrField.convertFieldToString(42));
    }

    @Test
    void convertFieldToStringShouldHandleArrayListWithLong() {
        ArrayList<Long> list = new ArrayList<>();
        list.add(999L);
        assertEquals("999", TurSolrField.convertFieldToString(list));
    }

    @Test
    void convertFieldToStringShouldHandleArrayListWithDate() {
        ArrayList<Date> list = new ArrayList<>();
        list.add(new Date(0L));
        assertEquals("1970-01-01T00:00:00Z", TurSolrField.convertFieldToString(list));
    }

    @Test
    void convertFieldToStringShouldHandleArrayListWithNull() {
        ArrayList<Object> list = new ArrayList<>();
        list.add(null);
        assertEquals("", TurSolrField.convertFieldToString(list));
    }
}
