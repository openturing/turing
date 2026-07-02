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

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;

/**
 * @author Alexandre Oliveira
 * @since 0.3.7
 */
public interface TurSNRankingExpressionRepository extends JpaRepository<TurSNRankingExpression, String> {

	public String FIND_BY_TUR_SN_SITE = "turSNRankingExpressionFindByTurSNSite";

	// JOIN FETCH eagerly initializes the LAZY turSNRankingConditions collection
	// within the query session. Because the result is @Cacheable, the cached
	// entities are detached with no live session; without this, DTO/domain mappers
	// that read the collection on a cache hit throw LazyInitializationException.
	@Cacheable(FIND_BY_TUR_SN_SITE)
	@Query("select distinct e from TurSNRankingExpression e "
			+ "left join fetch e.turSNRankingConditions where e.turSNSite = :turSNSite")
	Set<TurSNRankingExpression> findByTurSNSite(Sort language, @Param("turSNSite") TurSNSite turSNSite);

	@CacheEvict(value = { FIND_BY_TUR_SN_SITE }, allEntries = true)
	@NotNull
	@Override
	<S extends TurSNRankingExpression> S save(@NotNull S entity);

	@Override
	@CacheEvict(value = { FIND_BY_TUR_SN_SITE }, allEntries = true)
	void delete(@NotNull TurSNRankingExpression turSNRankingExpression);
}
