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
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

/**
 * Per-field analyzer that routes {@code *_ac} fields to
 * {@link TurLuceneEdgeNGramAnalyzer} and all other fields
 * to {@link StandardAnalyzer}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurLucenePerFieldAnalyzer extends DelegatingAnalyzerWrapper {

    private final Analyzer defaultAnalyzer = new StandardAnalyzer();
    private final Analyzer edgeNGramAnalyzer = new TurLuceneEdgeNGramAnalyzer(
            TurLuceneConstants.AC_MIN_GRAM, TurLuceneConstants.AC_MAX_GRAM);

    public TurLucenePerFieldAnalyzer() {
        super(PER_FIELD_REUSE_STRATEGY);
    }

    @Override
    protected Analyzer getWrappedAnalyzer(String fieldName) {
        if (fieldName != null && fieldName.endsWith(TurLuceneConstants.AC_SUFFIX)) {
            return edgeNGramAnalyzer;
        }
        return defaultAnalyzer;
    }
}
