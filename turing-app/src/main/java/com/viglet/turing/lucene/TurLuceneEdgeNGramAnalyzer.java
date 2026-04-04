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
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * Analyzer that produces edge n-grams for autocomplete.
 * Pipeline: StandardTokenizer → LowerCaseFilter → EdgeNGramTokenFilter.
 *
 * <p>For example, "Dragon" is tokenized as: "d", "dr", "dra", "drag", "drago", "dragon"
 * (with minGram=2: "dr", "dra", "drag", "drago", "dragon").
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurLuceneEdgeNGramAnalyzer extends Analyzer {

    private final int minGram;
    private final int maxGram;

    public TurLuceneEdgeNGramAnalyzer(int minGram, int maxGram) {
        this.minGram = minGram;
        this.maxGram = maxGram;
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        StandardTokenizer tokenizer = new StandardTokenizer();
        TokenStream filter = new LowerCaseFilter(tokenizer);
        filter = new EdgeNGramTokenFilter(filter, minGram, maxGram, false);
        return new TokenStreamComponents(tokenizer, filter);
    }
}
