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
package com.viglet.turing.sn.kb;

import java.text.Normalizer;
import java.util.Locale;

/**
 * T671 / §XL (Block AQ) — length-preserving text folding for the microthesaurus
 * recognition matcher. Both the document text and every surface form are folded
 * the same way (lowercase + accent-strip) so a single Aho-Corasick automaton can
 * be built over the most permissive form; the per-variation {@code case}/
 * {@code accent} flags are then re-verified against the <em>original</em> slice
 * (see {@link TurRecognitionDictionary}).
 *
 * <p><strong>Length-preserving is the key property.</strong> Folding one
 * character at a time and keeping only the first base char of its canonical
 * decomposition yields exactly one output char per input char, so a match offset
 * in the folded text is also a valid offset in the original text — no position
 * map is needed. Rare multi-char expansions (ligatures) collapse to their base;
 * that is acceptable for term recognition.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurTextNormalizer {

    private TurTextNormalizer() {
    }

    /** Folds to lowercase, accent-stripped, length-preserving. */
    public static String fold(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            sb.append(foldChar(value.charAt(i)));
        }
        return sb.toString();
    }

    /** Strips accents but preserves case, length-preserving. */
    public static String foldAccentsKeepCase(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            sb.append(stripAccent(value.charAt(i)));
        }
        return sb.toString();
    }

    private static char foldChar(char c) {
        return Character.toLowerCase(stripAccent(c));
    }

    private static char stripAccent(char c) {
        if (c < 0x80) {
            return c;
        }
        String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
        for (int i = 0; i < decomposed.length(); i++) {
            char d = decomposed.charAt(i);
            if (Character.getType(d) != Character.NON_SPACING_MARK) {
                return d;
            }
        }
        return c;
    }

    /** A char that can be part of a recognised token (letters/digits). */
    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c);
    }
}
