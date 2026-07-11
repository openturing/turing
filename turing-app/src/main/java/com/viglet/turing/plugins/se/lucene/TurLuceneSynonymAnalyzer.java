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
package com.viglet.turing.plugins.se.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;

/**
 * T665 / §XXXIX (Block AP) — a query-time analyzer that mirrors
 * {@code StandardAnalyzer}'s tokenize + lowercase, then applies a
 * {@link SynonymGraphFilter} built from the site's {@link SynonymMap}. Used only
 * on the query side (never at index time), so synonym edits take effect on the
 * next search with no reindex.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public class TurLuceneSynonymAnalyzer extends Analyzer {

    private final SynonymMap synonymMap;

    public TurLuceneSynonymAnalyzer(SynonymMap synonymMap) {
        this.synonymMap = synonymMap;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new StandardTokenizer();
        TokenStream tokens = new LowerCaseFilter(source);
        tokens = new SynonymGraphFilter(tokens, synonymMap, true);
        return new TokenStreamComponents(source, tokens);
    }
}
