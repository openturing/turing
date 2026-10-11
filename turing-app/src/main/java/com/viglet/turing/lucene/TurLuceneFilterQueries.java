/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.lucene;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;

import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Turns the value of one {@code field:value} filter query into a Lucene query
 * that matches how {@link TurLuceneDocumentHandler} indexed the field (T1075).
 * Numeric and date fields are indexed as points, so a plain {@link TermQuery}
 * never matches them; this reads the value by the field's type instead.
 *
 * <p>
 * Accepts Solr's range syntax, {@code [from TO to]} with {@code {}} for an
 * exclusive bound and {@code *} for an open one, and on DATE fields Solr date
 * math: {@code NOW}, an ISO instant or a {@code yyyy-MM-dd} day, followed by
 * {@code +n}/{@code -n} steps and {@code /UNIT} rounding (e.g.
 * {@code NOW-7DAYS/DAY}). An exact value that does not parse for its type
 * falls back to a term lookup, which is what a value the handler could not
 * parse was indexed as; a range that does not parse matches nothing.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurLuceneFilterQueries {

    private static final Pattern RANGE = Pattern.compile("^([\\[{])\\s*(\\S+)\\s+TO\\s+(\\S+)\\s*([]}])$");
    private static final Pattern DATE_MATH_STEP = Pattern.compile("([+-]\\d+|/)([A-Za-z]+)");
    private static final String OPEN = "*";

    private TurLuceneFilterQueries() {
    }

    /**
     * @param field the filtered field
     * @param value the filter's value, unquoted
     * @param type  the field's type, or {@code null} when the field is unknown
     * @param now   the instant {@code NOW} resolves to
     */
    public static Query build(String field, String value, TurSEFieldType type, Instant now) {
        Matcher range = RANGE.matcher(value.trim());
        if (range.matches()) {
            boolean lowerInclusive = "[".equals(range.group(1));
            boolean upperInclusive = "]".equals(range.group(4));
            Query query = rangeQuery(field, type, range.group(2), lowerInclusive, range.group(3),
                    upperInclusive, now);
            return query != null ? query : new MatchNoDocsQuery("unparseable range on " + field);
        }
        Query exact = exactQuery(field, value, type, now);
        return exact != null ? exact : new TermQuery(new Term(field, value));
    }

    private static Query exactQuery(String field, String value, TurSEFieldType type, Instant now) {
        if (type == null) {
            return null;
        }
        try {
            String trimmed = value.trim();
            return switch (type) {
                case INT -> IntPoint.newExactQuery(field, Integer.parseInt(trimmed));
                case LONG -> LongPoint.newExactQuery(field, Long.parseLong(trimmed));
                case FLOAT -> FloatPoint.newExactQuery(field, Float.parseFloat(trimmed));
                case DOUBLE, CURRENCY -> DoublePoint.newExactQuery(field, Double.parseDouble(trimmed));
                case DATE -> LongPoint.newExactQuery(field, parseDate(trimmed, now).toEpochMilli());
                default -> null;
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }

    private static Query rangeQuery(String field, TurSEFieldType type, String from, boolean fromInclusive,
            String to, boolean toInclusive, Instant now) {
        try {
            if (type == null) {
                return termRange(field, from, fromInclusive, to, toInclusive);
            }
            return switch (type) {
                case INT -> IntPoint.newRangeQuery(field,
                        lowerInt(from, fromInclusive), upperInt(to, toInclusive));
                case LONG -> LongPoint.newRangeQuery(field,
                        lowerLong(OPEN.equals(from) ? null : Long.parseLong(from), fromInclusive),
                        upperLong(OPEN.equals(to) ? null : Long.parseLong(to), toInclusive));
                case DATE -> LongPoint.newRangeQuery(field,
                        lowerLong(OPEN.equals(from) ? null : parseDate(from, now).toEpochMilli(), fromInclusive),
                        upperLong(OPEN.equals(to) ? null : parseDate(to, now).toEpochMilli(), toInclusive));
                case FLOAT -> FloatPoint.newRangeQuery(field,
                        lowerFloat(from, fromInclusive), upperFloat(to, toInclusive));
                case DOUBLE, CURRENCY -> DoublePoint.newRangeQuery(field,
                        lowerDouble(from, fromInclusive), upperDouble(to, toInclusive));
                default -> termRange(field, from, fromInclusive, to, toInclusive);
            };
        } catch (NumberFormatException | DateTimeParseException | ArithmeticException e) {
            return null;
        }
    }

    private static Query termRange(String field, String from, boolean fromInclusive, String to,
            boolean toInclusive) {
        return TermRangeQuery.newStringRange(field, OPEN.equals(from) ? null : from,
                OPEN.equals(to) ? null : to, fromInclusive, toInclusive);
    }

    private static int lowerInt(String bound, boolean inclusive) {
        if (OPEN.equals(bound)) return Integer.MIN_VALUE;
        int value = Integer.parseInt(bound);
        return inclusive ? value : Math.addExact(value, 1);
    }

    private static int upperInt(String bound, boolean inclusive) {
        if (OPEN.equals(bound)) return Integer.MAX_VALUE;
        int value = Integer.parseInt(bound);
        return inclusive ? value : Math.subtractExact(value, 1);
    }

    private static long lowerLong(Long value, boolean inclusive) {
        if (value == null) return Long.MIN_VALUE;
        return inclusive ? value : Math.addExact(value, 1L);
    }

    private static long upperLong(Long value, boolean inclusive) {
        if (value == null) return Long.MAX_VALUE;
        return inclusive ? value : Math.subtractExact(value, 1L);
    }

    private static float lowerFloat(String bound, boolean inclusive) {
        if (OPEN.equals(bound)) return Float.NEGATIVE_INFINITY;
        float value = Float.parseFloat(bound);
        return inclusive ? value : Math.nextUp(value);
    }

    private static float upperFloat(String bound, boolean inclusive) {
        if (OPEN.equals(bound)) return Float.POSITIVE_INFINITY;
        float value = Float.parseFloat(bound);
        return inclusive ? value : Math.nextDown(value);
    }

    private static double lowerDouble(String bound, boolean inclusive) {
        if (OPEN.equals(bound)) return Double.NEGATIVE_INFINITY;
        double value = Double.parseDouble(bound);
        return inclusive ? value : Math.nextUp(value);
    }

    private static double upperDouble(String bound, boolean inclusive) {
        if (OPEN.equals(bound)) return Double.POSITIVE_INFINITY;
        double value = Double.parseDouble(bound);
        return inclusive ? value : Math.nextDown(value);
    }

    /**
     * Parses a date with Solr date math, in UTC. The anchor is {@code NOW}, an
     * ISO instant or a {@code yyyy-MM-dd} day; each following step adds or
     * subtracts units ({@code -7DAYS}) or rounds down to one ({@code /DAY}).
     */
    static Instant parseDate(String value, Instant now) {
        String upper = value.toUpperCase(Locale.ROOT);
        ZonedDateTime anchor;
        String math;
        if (upper.startsWith("NOW")) {
            anchor = now.atZone(ZoneOffset.UTC);
            math = value.substring(3);
        } else {
            int split = mathStart(value);
            String base = value.substring(0, split);
            anchor = parseAnchor(base);
            math = value.substring(split);
        }
        return applyMath(anchor, math).toInstant();
    }

    /** Where date math begins after an ISO anchor: the first {@code /}, or a sign after its 'Z'. */
    private static int mathStart(String value) {
        int z = value.toUpperCase(Locale.ROOT).indexOf('Z');
        int from = z >= 0 ? z + 1 : Math.min(value.length(), 10);
        for (int i = from; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '+' || c == '-' || c == '/') {
                return i;
            }
        }
        return value.length();
    }

    private static ZonedDateTime parseAnchor(String base) {
        try {
            return Instant.parse(base).atZone(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return LocalDate.parse(base).atStartOfDay(ZoneOffset.UTC);
        }
    }

    private static ZonedDateTime applyMath(ZonedDateTime anchor, String math) {
        ZonedDateTime result = anchor;
        int position = 0;
        Matcher step = DATE_MATH_STEP.matcher(math);
        while (position < math.length()) {
            if (!step.find(position) || step.start() != position) {
                throw new DateTimeParseException("Unparseable date math", math, position);
            }
            ChronoUnit unit = unit(step.group(2), math, position);
            result = "/".equals(step.group(1))
                    ? round(result, unit)
                    : result.plus(Long.parseLong(step.group(1)), unit);
            position = step.end();
        }
        return result;
    }

    private static ChronoUnit unit(String name, String math, int position) {
        return switch (name.toUpperCase(Locale.ROOT)) {
            case "YEAR", "YEARS" -> ChronoUnit.YEARS;
            case "MONTH", "MONTHS" -> ChronoUnit.MONTHS;
            case "DAY", "DAYS", "DATE" -> ChronoUnit.DAYS;
            case "HOUR", "HOURS" -> ChronoUnit.HOURS;
            case "MINUTE", "MINUTES" -> ChronoUnit.MINUTES;
            case "SECOND", "SECONDS" -> ChronoUnit.SECONDS;
            case "MILLI", "MILLIS", "MILLISECOND", "MILLISECONDS" -> ChronoUnit.MILLIS;
            default -> throw new DateTimeParseException("Unknown date math unit " + name, math, position);
        };
    }

    private static ZonedDateTime round(ZonedDateTime value, ChronoUnit unit) {
        return switch (unit) {
            case YEARS -> value.truncatedTo(ChronoUnit.DAYS).withDayOfYear(1);
            case MONTHS -> value.truncatedTo(ChronoUnit.DAYS).withDayOfMonth(1);
            default -> value.truncatedTo(unit);
        };
    }
}
