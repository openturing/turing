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
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * T1064 — translates a site's ranking expressions into Lucene boost clauses, the
 * Lucene counterpart of {@code TurSolrQueryBuilder.prepareBoostQuery}.
 *
 * <p>Each expression becomes one {@link BoostQuery} weighted by the expression's
 * weight, whose conditions must all match, the way Solr's boost query ANDs them.
 * The caller adds each clause as {@code SHOULD} next to the main query, so it
 * raises the score of matching documents without changing which documents match.
 * A condition on a DATE field with value {@code asc} is a recency boost on the
 * same curve as Solr's {@code recip(ms(NOW/DAY,field),3.16e-11,1,1)}. Any other
 * condition is an exact match on the field, typed by the field's configuration.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurLuceneRankingExpressions {

    private static final String ASC = "asc";

    /**
     * Solr's {@code recip(x, m, 1, 1) = 1 / (m·x + 1)} with {@code m = 3.16e-11}
     * equals Lucene's distance-feature score {@code pivot / (pivot + x)} with
     * {@code pivot = 1 / m}: a document about a year old scores half of one from today.
     */
    static final long RECENCY_PIVOT_MS = Math.round(1 / 3.16e-11);

    private final TurSNRankingExpressionRepository turSNRankingExpressionRepository;
    private final TurSNRankingConditionRepository turSNRankingConditionRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    public TurLuceneRankingExpressions(TurSNRankingExpressionRepository turSNRankingExpressionRepository,
            TurSNRankingConditionRepository turSNRankingConditionRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNRankingExpressionRepository = turSNRankingExpressionRepository;
        this.turSNRankingConditionRepository = turSNRankingConditionRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    /** The boost clauses for {@code turSNSite}'s ranking expressions; empty when it has none. */
    public List<Query> boostQueries(TurSNSite turSNSite) {
        return boostQueries(turSNSite, System.currentTimeMillis());
    }

    List<Query> boostQueries(TurSNSite turSNSite, long nowMs) {
        Set<TurSNRankingExpression> expressions = turSNRankingExpressionRepository
                .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite);
        if (expressions == null || expressions.isEmpty()) {
            return List.of();
        }
        Map<String, TurSEFieldType> fieldTypes = fieldTypes(turSNSite);
        // Solr's NOW/DAY: the start of the current UTC day.
        long origin = Instant.ofEpochMilli(nowMs).truncatedTo(ChronoUnit.DAYS).toEpochMilli();
        List<Query> boosts = new ArrayList<>();
        for (TurSNRankingExpression expression : expressions) {
            Query boost = expressionQuery(expression, fieldTypes, origin);
            if (boost != null) {
                boosts.add(boost);
            }
        }
        return boosts;
    }

    private Query expressionQuery(TurSNRankingExpression expression, Map<String, TurSEFieldType> fieldTypes,
            long origin) {
        Set<TurSNRankingCondition> conditions = turSNRankingConditionRepository
                .findByTurSNRankingExpression(expression);
        if (conditions == null || conditions.isEmpty()) {
            return null;
        }
        float weight = expression.getWeight();
        if (!(weight > 0) || Float.isInfinite(weight)) {
            log.debug("[Lucene] ranking expression '{}' skipped: weight {} is not a positive boost",
                    expression.getName(), weight);
            return null;
        }
        BooleanQuery.Builder all = new BooleanQuery.Builder();
        for (TurSNRankingCondition condition : conditions) {
            Query query = conditionQuery(condition, fieldTypes.get(condition.getAttribute()), origin);
            if (query == null) {
                // A condition that cannot be expressed would otherwise widen the
                // expression to documents it does not describe.
                log.debug("[Lucene] ranking expression '{}' skipped: condition {}:{} cannot be applied",
                        expression.getName(), condition.getAttribute(), condition.getValue());
                return null;
            }
            all.add(query, BooleanClause.Occur.MUST);
        }
        return new BoostQuery(all.build(), weight);
    }

    private static Query conditionQuery(TurSNRankingCondition condition, TurSEFieldType type, long origin) {
        String field = condition.getAttribute();
        String value = condition.getValue();
        if (!StringUtils.hasText(field)) {
            return null;
        }
        if (condition.getCondition() == TurSNRankingCondition.EXISTS) {
            // Every indexed type carries doc values on the field's own name
            // (TurLuceneDocumentHandler), which is what FieldExistsQuery reads.
            return new FieldExistsQuery(field);
        }
        if (value == null) {
            return null;
        }
        if (type == null) {
            return new TermQuery(new Term(field, value));
        }
        try {
            return switch (type) {
                case DATE -> ASC.equalsIgnoreCase(value)
                        ? LongField.newDistanceFeatureQuery(field, 1f, origin, RECENCY_PIVOT_MS)
                        : LongPoint.newExactQuery(field, Instant.parse(value).toEpochMilli());
                case TEXT -> phraseQuery(field, value);
                case INT -> IntPoint.newExactQuery(field, Integer.parseInt(value.trim()));
                case LONG -> LongPoint.newExactQuery(field, Long.parseLong(value.trim()));
                case FLOAT -> FloatPoint.newExactQuery(field, Float.parseFloat(value.trim()));
                case DOUBLE, CURRENCY -> DoublePoint.newExactQuery(field, Double.parseDouble(value.trim()));
                default -> new TermQuery(new Term(field, value));
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }

    /** Solr's {@code field:"value"} on an analyzed field: the value as a phrase. */
    private static Query phraseQuery(String field, String value) {
        try {
            return new QueryParser(field, new StandardAnalyzer())
                    .parse("\"" + QueryParserBase.escape(value) + "\"");
        } catch (ParseException e) {
            return new TermQuery(new Term(field, value.toLowerCase()));
        }
    }

    private Map<String, TurSEFieldType> fieldTypes(TurSNSite turSNSite) {
        Map<String, TurSEFieldType> types = new HashMap<>();
        List<TurSNSiteFieldExt> fields = turSNSiteFieldExtRepository
                .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite);
        if (fields != null) {
            fields.stream()
                    .filter(field -> field.getName() != null && field.getType() != null)
                    .forEach(field -> types.putIfAbsent(field.getName(), field.getType()));
        }
        return types;
    }
}
