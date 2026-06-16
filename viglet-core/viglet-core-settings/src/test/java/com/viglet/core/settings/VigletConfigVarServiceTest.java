/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Behavior of {@link VigletConfigVarService} over an in-memory
 * {@link VigletConfigVarStore} fake — exercising the parse/clamp/trim/enum
 * logic lifted from the three product implementations.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class VigletConfigVarServiceTest {

    private static final String PATH = "/system/global";

    private MapStore store;
    private VigletConfigVarService service;

    enum Sep {
        DOT, COMMA
    }

    @BeforeEach
    void setUp() {
        store = new MapStore();
        service = new VigletConfigVarService(store, PATH);
    }

    // ---- strings ----

    @Test
    void getStringReturnsDefaultWhenAbsent() {
        assertThat(service.getString("x", "")).isEmpty();
        assertThat(service.getString("x", "fallback")).isEqualTo("fallback");
    }

    @Test
    void setStringTrimsAndCoalescesNull() {
        assertThat(service.setString("x", "  hi  ")).isEqualTo("hi");
        assertThat(service.getString("x", "")).isEqualTo("hi");
        assertThat(service.setString("y", null)).isEmpty();
        assertThat(store.rows.get("x").path()).isEqualTo(PATH);
    }

    @Test
    void getStringNonBlankFallsBackOnMissingOrBlank() {
        assertThat(service.getStringNonBlank("img", "python:3.12-slim")).isEqualTo("python:3.12-slim");
        store.put("img", PATH, "   ");
        assertThat(service.getStringNonBlank("img", "python:3.12-slim")).isEqualTo("python:3.12-slim");
        store.put("img", PATH, "  my/image:1  ");
        assertThat(service.getStringNonBlank("img", "python:3.12-slim")).isEqualTo("my/image:1");
    }

    @Test
    void setRawStoresVerbatim() {
        service.setRaw("s", "  Not/Trimmed  ");
        assertThat(store.rows.get("s").value()).isEqualTo("  Not/Trimmed  ");
    }

    // ---- booleans ----

    @Test
    void getBooleanParsesStrictlyAndDefaults() {
        assertThat(service.getBoolean("b", false)).isFalse();
        store.put("b", PATH, "true");
        assertThat(service.getBoolean("b", false)).isTrue();
        store.put("b", PATH, "yes");
        assertThat(service.getBoolean("b", true)).isFalse();
    }

    @Test
    void setBooleanRoundTrips() {
        assertThat(service.setBoolean("b", true)).isTrue();
        assertThat(service.getBoolean("b", false)).isTrue();
    }

    // ---- ints / clamp ----

    @Test
    void getIntDefaultsOnGarbage() {
        store.put("n", PATH, "notanumber");
        assertThat(service.getInt("n", 20)).isEqualTo(20);
        store.put("n", PATH, "  42 ");
        assertThat(service.getInt("n", 20)).isEqualTo(42);
    }

    @Test
    void getIntClampedBounds() {
        store.put("n", PATH, "500");
        assertThat(service.getIntClamped("n", 20, 1, 100)).isEqualTo(100);
        store.put("n", PATH, "bad");
        assertThat(service.getIntClamped("n", 20, 1, 100)).isEqualTo(20);
    }

    @Test
    void setIntClampedClampsBeforeStore() {
        assertThat(service.setIntClamped("ttl", 100000, 0, 8760)).isEqualTo(8760);
        assertThat(service.getInt("ttl", 0)).isEqualTo(8760);
        assertThat(service.setIntClamped("ttl", -5, 0, 8760)).isZero();
    }

    // ---- longs ----

    @Test
    void getLongDefaultsOnGarbage() {
        assertThat(service.getLong("ms", 3_600_000L)).isEqualTo(3_600_000L);
        store.put("ms", PATH, "5000");
        assertThat(service.getLong("ms", 3_600_000L)).isEqualTo(5000L);
        store.put("ms", PATH, "x");
        assertThat(service.getLong("ms", 3_600_000L)).isEqualTo(3_600_000L);
    }

    // ---- doubles / clamp ----

    @Test
    void getDoubleClampedBounds() {
        store.put("t", PATH, "5.0");
        assertThat(service.getDoubleClamped("t", 0.0, 0.0, 1.0)).isEqualTo(1.0);
        store.put("t", PATH, "garbage");
        assertThat(service.getDoubleClamped("t", 0.0, 0.0, 1.0)).isEqualTo(0.0);
        store.put("t", PATH, "0.42");
        assertThat(service.getDoubleClamped("t", 0.0, 0.0, 1.0)).isEqualTo(0.42);
    }

    // ---- enums ----

    @Test
    void getEnumCaseInsensitiveWithFallback() {
        assertThat(service.getEnum(Sep.class, "sep", Sep.DOT)).isEqualTo(Sep.DOT);
        store.put("sep", PATH, "comma");
        assertThat(service.getEnum(Sep.class, "sep", Sep.DOT)).isEqualTo(Sep.COMMA);
        store.put("sep", PATH, "  DOT  ");
        assertThat(service.getEnum(Sep.class, "sep", Sep.COMMA)).isEqualTo(Sep.DOT);
        store.put("sep", PATH, "INVALID");
        assertThat(service.getEnum(Sep.class, "sep", Sep.DOT)).isEqualTo(Sep.DOT);
    }

    @Test
    void setEnumStoresNameAndCoalescesNull() {
        assertThat(service.setEnum("sep", Sep.COMMA, Sep.DOT)).isEqualTo(Sep.COMMA);
        assertThat(store.rows.get("sep").value()).isEqualTo("COMMA");
        assertThat(service.setEnum("sep", null, Sep.DOT)).isEqualTo(Sep.DOT);
        assertThat(store.rows.get("sep").value()).isEqualTo("DOT");
    }

    // ---- lifecycle / path ----

    @Test
    void existsAndDelete() {
        assertThat(service.exists("k")).isFalse();
        service.setString("k", "v");
        assertThat(service.exists("k")).isTrue();
        service.delete("k");
        assertThat(service.exists("k")).isFalse();
    }

    @Test
    void getVariablesFromPathGroups() {
        service.setString("a", "1");
        service.setString("b", "2");
        store.put("c", "/other", "3");
        assertThat(service.getVariablesFromPath(PATH)).containsOnlyKeys("a", "b");
    }

    @Test
    void defaultPathIsExposed() {
        assertThat(service.getDefaultPath()).isEqualTo(PATH);
        assertThat(new VigletConfigVarService(store, null).getDefaultPath()).isEmpty();
    }

    /** Minimal in-memory {@link VigletConfigVarStore} for the tests. */
    private static final class MapStore implements VigletConfigVarStore {
        record Row(String path, String value) {
        }

        final Map<String, Row> rows = new LinkedHashMap<>();

        @Override
        public Optional<String> find(String id) {
            return Optional.ofNullable(rows.get(id)).map(Row::value);
        }

        @Override
        public void put(String id, String path, String value) {
            rows.put(id, new Row(path, value));
        }

        @Override
        public Map<String, String> findByPath(String path) {
            Map<String, String> out = new LinkedHashMap<>();
            rows.forEach((k, v) -> {
                if (v.path().equals(path)) {
                    out.put(k, v.value());
                }
            });
            return out;
        }

        @Override
        public boolean exists(String id) {
            return rows.containsKey(id);
        }

        @Override
        public void remove(String id) {
            rows.remove(id);
        }
    }
}
