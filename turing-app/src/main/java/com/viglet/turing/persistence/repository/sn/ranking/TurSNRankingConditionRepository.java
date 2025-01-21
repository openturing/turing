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
package com.viglet.turing.persistence.repository.sn.ranking;

import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

/**
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
public interface TurSNRankingConditionRepository extends JpaRepository<TurSNRankingCondition, String> {

	public String FIND_BY_TUR_SN_RANKING_EXPRESSION = "turSNRankingConditionFindByTurSNRankingExpression";

	@Cacheable(FIND_BY_TUR_SN_RANKING_EXPRESSION)
	Set<TurSNRankingCondition> findByTurSNRankingExpression(TurSNRankingExpression turSNRankingExpression);

	@CacheEvict(value = {FIND_BY_TUR_SN_RANKING_EXPRESSION}, allEntries = true)
	@NotNull
	TurSNRankingCondition save(@NotNull TurSNRankingCondition turSNRankingCondition);

	@CacheEvict(value = {FIND_BY_TUR_SN_RANKING_EXPRESSION}, allEntries = true)
	void delete(@NotNull TurSNRankingCondition turSNRankingCondition);
}
