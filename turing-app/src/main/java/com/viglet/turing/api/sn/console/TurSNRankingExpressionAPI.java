/*
 * Copyright (C) 2016-2023 the original author or authors.
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

package com.viglet.turing.api.sn.console;

import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Alexandre Oliveira
 * @since 0.3.7
 */

@RestController
@RequestMapping("/api/sn/{snSiteId}/ranking-expression")
@Tag(name = "Semantic Navigation Ranking Expression", description = "Semantic Navigation Ranking Expression API")
public class TurSNRankingExpressionAPI {

    @Autowired
    private TurSNSiteRepository turSNSiteRepository;
    @Autowired
    private TurSNRankingExpressionRepository turSNRankingExpressionRepository;
    @Autowired
    private TurSNRankingConditionRepository turSNRankingConditionRepository;

    @Operation(summary = "Semantic Navigation Ranking Expression List")
    @GetMapping
    public Set<TurSNRankingExpression> turSNRankingExpressionList(@PathVariable String snSiteId) {
        return turSNSiteRepository.findById(snSiteId).map(this.turSNRankingExpressionRepository::findByTurSNSite)
                .orElse(new HashSet<>());
    }

    @Operation(summary = "Show a Semantic Navigation Ranking Expression")
    @GetMapping("/{id}")
    public TurSNRankingExpression turSNRankingExpressionGet(@PathVariable String snSiteId, @PathVariable String id) {

        Optional<TurSNRankingExpression> turSNRankingExpression = turSNRankingExpressionRepository.findById(id);
        if (turSNRankingExpression.isPresent()) {
            turSNRankingExpression.get().setTurSNRankingConditions(
                    turSNRankingConditionRepository.findByTurSNRankingExpression(turSNRankingExpression.get()));
            return turSNRankingExpression.get();
        }

        return new TurSNRankingExpression();
    }

    @Operation(summary = "Update a Semantic Navigation Ranking Expression")
    @PutMapping("/{id}")
    public TurSNRankingExpression turSNRankingExpressionUpdate(@PathVariable String id,
                                                               @RequestBody TurSNRankingExpression turSNRankingExpression,
                                                               @PathVariable String snSiteId) {

        return turSNRankingExpressionRepository.findById(id).map(turSNRankingExpressionEdit -> {
            turSNRankingExpressionEdit.setWeight(turSNRankingExpression.getWeight());
            turSNRankingExpressionEdit.setName(turSNRankingExpression.getName());
            turSNRankingExpressionEdit.setTurSNRankingConditions(turSNRankingExpression.getTurSNRankingConditions()
                    .stream()
                    .peek(condition ->
                            condition.setTurSNRankingExpression(turSNRankingExpression))
                    .collect(Collectors.toSet()));
            turSNRankingExpressionRepository.save(turSNRankingExpressionEdit);

            return turSNRankingExpressionEdit;
        }).orElse(new TurSNRankingExpression());
    }

    @Transactional
    @Operation(summary = "Delete a Semantic Navigation Ranking Expression")
    @DeleteMapping("/{id}")
    @CacheEvict(value = {"ranking_expression"}, allEntries = true)
    public boolean turSNRankingExpressionDelete(@PathVariable String id, @PathVariable String snSiteId) {
        turSNRankingExpressionRepository.deleteById(id);
        return true;
    }

    @Operation(summary = "Create a Semantic Navigation Ranking Expression")
    @PostMapping
    public TurSNRankingExpression turSNRankingExpressionAdd(@RequestBody TurSNRankingExpression turSNRankingExpression, @PathVariable String snSiteId) {
        turSNRankingExpression.setTurSNRankingConditions(turSNRankingExpression.getTurSNRankingConditions()
                .stream()
                .peek(condition ->
                        condition.setTurSNRankingExpression(turSNRankingExpression))
                .collect(Collectors.toSet()));
        turSNRankingExpressionRepository.save(turSNRankingExpression);
        return turSNRankingExpression;
    }

    @Operation(summary = "Semantic Navigation Ranking Expression Structure")
    @GetMapping("structure")
    public TurSNRankingExpression turSNRankingExpressionStructure(@PathVariable String snSiteId) {
        return turSNSiteRepository.findById(snSiteId).map(turSNSite -> {
            TurSNRankingExpression turSNRankingExpression = new TurSNRankingExpression();
            turSNRankingExpression.setTurSNSite(turSNSite);
            return turSNRankingExpression;
        }).orElse(new TurSNRankingExpression());
    }
}