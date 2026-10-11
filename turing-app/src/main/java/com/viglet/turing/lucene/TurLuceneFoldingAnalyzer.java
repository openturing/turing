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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * T1080 — {@code StandardAnalyzer}'s tokenize + lowercase, then ASCII folding, so
 * an accented and an unaccented spelling match each other ({@code saude} finds
 * {@code saúde}, {@code sao paulo} finds {@code São Paulo}).
 *
 * <p>The folded token is added <em>beside</em> the original, at the same position
 * ({@code preserveOriginal}), on both the index and the query side. That keeps an
 * index written before T1080 working: it holds only the accented terms, which an
 * accented query still produces. An unaccented query matches such a document once
 * it is re-indexed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurLuceneFoldingAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new StandardTokenizer();
        return new TokenStreamComponents(source, fold(new LowerCaseFilter(source)));
    }

    /** Lowercase only, as StandardAnalyzer: a prefix term keeps the spelling typed. */
    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
    }

    /** Adds the ASCII-folded form beside each token that has one. */
    public static TokenStream fold(TokenStream tokens) {
        return new ASCIIFoldingFilter(tokens, true);
    }
}
