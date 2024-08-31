/*
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.lucene;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.QueryBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.viglet.turing.lucene.TurLuceneConstants.FULL_TEXT;
import static com.viglet.turing.lucene.TurLuceneUtils.getLuceneDirectory;

@Slf4j
@Component
public class TurLuceneSearch {
    public List<Document> search(String query) {
        return getLuceneDirectory().map(directory -> {
            try {
                IndexSearcher searcher = getIndexSearcher(directory);
                QueryBuilder queryBuilder = new QueryBuilder(new StandardAnalyzer());
                Query phraseQuery = queryBuilder.createPhraseQuery(FULL_TEXT, query);
                Query termQuery = new TermQuery(new Term(FULL_TEXT, query));

                BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
                booleanQueryBuilder.add(phraseQuery, BooleanClause.Occur.SHOULD);
                booleanQueryBuilder.add(termQuery, BooleanClause.Occur.SHOULD);
                BooleanQuery booleanQuery = booleanQueryBuilder.build();
                return getDocuments(searcher.search(booleanQuery, 100), searcher);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            return getEmptyDocumentList();
        }).orElseGet((Collections::emptyList));
    }

    @NotNull
    private static List<Document> getEmptyDocumentList() {
        return Collections.emptyList();
    }

    @NotNull
    private static IndexSearcher getIndexSearcher(FSDirectory directory) throws IOException {
        return new IndexSearcher(DirectoryReader.open(directory));
    }

    public List<Document> getDocumentByField(String name, String value) {
        return getLuceneDirectory().map(directory -> {
            try {
                IndexSearcher searcher = getIndexSearcher(directory);
                Query query = new QueryBuilder(new StandardAnalyzer()).createPhraseQuery(name, value);
                return getDocuments(searcher.search(query, 1), searcher);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
            return null;
        }).orElseGet((Collections::emptyList));
    }

    @NotNull
    private static List<Document> getDocuments(TopDocs topDocs, IndexSearcher searcher) {
        return Optional.ofNullable(topDocs).map(t -> {
            try {
                if (topDocs.scoreDocs != null && topDocs.scoreDocs.length > 0) {
                    log.info("Score: {}", topDocs.scoreDocs[0].score);
                    List<Document> documents = new ArrayList<>();
                    for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                        documents.add(searcher.storedFields().document(scoreDoc.doc));
                    }
                    return documents;
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            return null;
        }).orElseGet((Collections::emptyList));
    }
}
