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

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The typed accessor layer over a {@link VigletConfigVarStore}. It owns the
 * read/write boilerplate that Turing's {@code TurGlobalSettingsService}, Shio's
 * {@code ShConfigVarUtils} and Dumont's {@code DumConnectorConfigVarService}
 * each re-implemented by hand for every setting:
 *
 * <ul>
 *   <li><b>write</b> — null-coalesce to {@code ""}, trim, then upsert under a
 *       default {@code path};</li>
 *   <li><b>read</b> — parse-or-default for {@code boolean}/{@code int}/
 *       {@code long}/{@code double}, optional {@code [min, max]} clamping, and
 *       case-insensitive {@code valueOf}-style enum resolution that falls back
 *       to a default rather than throwing.</li>
 * </ul>
 *
 * <p>It holds no persistence dependency — only the injected port — so it stays
 * usable from any product regardless of its ORM. Secret encryption stays in the
 * product layer: a caller encrypts before {@link #setRaw} and decrypts after
 * {@link #getString}, since the cipher and key strategy are product concerns.
 *
 * <p>Lifted from the three duplicated implementations into {@code viglet-core}
 * (T376 / Block Q, STRATEGY §XII.2).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class VigletConfigVarService {

    private final VigletConfigVarStore store;
    private final String defaultPath;

    /**
     * @param store       the persistence port (product-supplied).
     * @param defaultPath the {@code path} stamped on every write that does not
     *                    pass an explicit one (e.g. Turing's
     *                    {@code "/system/global"}). {@code null} becomes
     *                    {@code ""}.
     */
    public VigletConfigVarService(VigletConfigVarStore store, String defaultPath) {
        this.store = store;
        this.defaultPath = defaultPath == null ? "" : defaultPath;
    }

    /** The {@code path} stamped on writes that do not pass an explicit one. */
    public String getDefaultPath() {
        return defaultPath;
    }

    // ---- raw / lifecycle -------------------------------------------------

    /** The raw stored value for {@code id}, if present. */
    public Optional<String> find(String id) {
        return store.find(id);
    }

    /** Whether a value is stored for {@code id}. */
    public boolean exists(String id) {
        return store.exists(id);
    }

    /** Delete the value for {@code id}. */
    public void delete(String id) {
        store.remove(id);
    }

    /** Every {@code key -> value} pair stored under {@code path}. */
    public Map<String, String> getVariablesFromPath(String path) {
        return store.findByPath(path);
    }

    // ---- strings ---------------------------------------------------------

    /** The stored value for {@code id}, or {@code defaultValue} when absent. */
    public String getString(String id, String defaultValue) {
        return store.find(id).orElse(defaultValue);
    }

    /**
     * The trimmed stored value for {@code id}, or {@code defaultValue} when the
     * row is absent or holds a blank value. Use for settings where blank means
     * "fall back to the documented default" (e.g. a Docker image name).
     */
    public String getStringNonBlank(String id, String defaultValue) {
        return store.find(id)
                .filter(v -> !v.isBlank())
                .map(String::trim)
                .orElse(defaultValue);
    }

    /**
     * Store {@code value} under the default path, coalescing {@code null} to
     * {@code ""} and trimming. Returns the value as stored.
     */
    public String setString(String id, String value) {
        return setString(id, defaultPath, value);
    }

    /** As {@link #setString(String, String)} but with an explicit {@code path}. */
    public String setString(String id, String path, String value) {
        String stored = value == null ? "" : value.trim();
        store.put(id, path, stored);
        return stored;
    }

    /**
     * Store {@code value} verbatim under the default path — no null-coalesce, no
     * trim. For pre-normalized values: enum names, encrypted secrets, numeric
     * strings the caller already formatted.
     */
    public void setRaw(String id, String value) {
        store.put(id, defaultPath, value);
    }

    // ---- booleans --------------------------------------------------------

    /**
     * Parse the stored value with {@link Boolean#parseBoolean} (so only the
     * literal {@code "true"}, case-insensitive, is true), or {@code defaultValue}
     * when absent.
     */
    public boolean getBoolean(String id, boolean defaultValue) {
        return store.find(id).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    /** Store {@code value} as {@code "true"}/{@code "false"}; returns it. */
    public boolean setBoolean(String id, boolean value) {
        store.put(id, defaultPath, String.valueOf(value));
        return value;
    }

    // ---- ints ------------------------------------------------------------

    /** Parse the stored value as an {@code int}, or {@code defaultValue} when absent or unparseable. */
    public int getInt(String id, int defaultValue) {
        return store.find(id).map(v -> parseIntOr(v, defaultValue)).orElse(defaultValue);
    }

    /** As {@link #getInt} but the result is clamped into {@code [min, max]}. */
    public int getIntClamped(String id, int defaultValue, int min, int max) {
        return clamp(getInt(id, defaultValue), min, max);
    }

    /** Store {@code value} as a decimal string; returns it. */
    public int setInt(String id, int value) {
        store.put(id, defaultPath, Integer.toString(value));
        return value;
    }

    /** Clamp {@code value} into {@code [min, max]}, store it, and return the clamped value. */
    public int setIntClamped(String id, int value, int min, int max) {
        return setInt(id, clamp(value, min, max));
    }

    // ---- longs -----------------------------------------------------------

    /** Parse the stored value as a {@code long}, or {@code defaultValue} when absent or unparseable. */
    public long getLong(String id, long defaultValue) {
        return store.find(id).map(v -> parseLongOr(v, defaultValue)).orElse(defaultValue);
    }

    /** Store {@code value} as a decimal string; returns it. */
    public long setLong(String id, long value) {
        store.put(id, defaultPath, Long.toString(value));
        return value;
    }

    // ---- doubles ---------------------------------------------------------

    /** Parse the stored value as a {@code double}, or {@code defaultValue} when absent or unparseable. */
    public double getDouble(String id, double defaultValue) {
        return store.find(id).map(v -> parseDoubleOr(v, defaultValue)).orElse(defaultValue);
    }

    /** As {@link #getDouble} but the result is clamped into {@code [min, max]}. */
    public double getDoubleClamped(String id, double defaultValue, double min, double max) {
        return clamp(getDouble(id, defaultValue), min, max);
    }

    /** Store {@code value} as a string; returns it. */
    public double setDouble(String id, double value) {
        store.put(id, defaultPath, Double.toString(value));
        return value;
    }

    // ---- enums -----------------------------------------------------------

    /**
     * Resolve the stored value to a constant of {@code type} case-insensitively
     * ({@code trim().toUpperCase(ROOT)} then {@code valueOf}), falling back to
     * {@code defaultValue} when the row is absent or holds an unknown value —
     * never throwing, so a corrupt row can never break a read path. Enums with
     * custom token parsing (aliases, lenient matching) keep their own parser and
     * use {@link #getString} instead.
     */
    public <E extends Enum<E>> E getEnum(Class<E> type, String id, E defaultValue) {
        return store.find(id).map(v -> parseEnumOr(type, v, defaultValue)).orElse(defaultValue);
    }

    /**
     * Store an enum by its {@link Enum#name()} under the default path, coalescing
     * {@code null} to {@code fallback}. Returns the value as stored.
     */
    public <E extends Enum<E>> E setEnum(String id, E value, E fallback) {
        E safe = value == null ? fallback : value;
        store.put(id, defaultPath, safe.name());
        return safe;
    }

    // ---- helpers ---------------------------------------------------------

    private static int parseIntOr(String value, int defaultValue) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLongOr(String value, long defaultValue) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseDoubleOr(String value, double defaultValue) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static <E extends Enum<E>> E parseEnumOr(Class<E> type, String value, E defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
