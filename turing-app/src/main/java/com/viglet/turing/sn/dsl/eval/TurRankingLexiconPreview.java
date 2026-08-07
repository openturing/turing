/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.sn.dsl.eval;

import java.util.List;

/**
 * T943 / §LVIII.8 (Block BJ) — what a site's ranking-lexicon bundle actually did,
 * answered before anyone has to notice that questions still rank the way they did.
 *
 * <p>It is a <strong>preview, not a validator</strong>: {@code error} says whether
 * the bundle parsed, the {@code added*} lists say what it contributed over the
 * shipped vocabulary, and — when the caller passed a sample question — {@code field}
 * and {@code order} are the same answer {@code detect} would give at request time.
 * A bundle that parses and adds words but resolves no sort is exactly the case a
 * yes/no validator would have called fine.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurRankingLexiconPreview(
        boolean valid,
        String error,
        String language,
        List<String> addedAscWords,
        List<String> addedDescWords,
        List<String> addedSortTriggers,
        List<String> addedSynonyms,
        boolean sortResolved,
        String field,
        String order,
        String strippedQuery) {

    /** The bundle could not be read — ranking runs on the shipped vocabulary alone. */
    public static TurRankingLexiconPreview invalid(String language, String error) {
        return new TurRankingLexiconPreview(false, error, language, List.of(), List.of(),
                List.of(), List.of(), false, null, null, null);
    }
}
