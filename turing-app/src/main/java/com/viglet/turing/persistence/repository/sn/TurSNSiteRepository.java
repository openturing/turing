/*
 * Copyright (C) 2016-2022 the original author or authors. 
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

package com.viglet.turing.persistence.repository.sn;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.viglet.turing.persistence.model.sn.TurSNSite;

public interface TurSNSiteRepository extends JpaRepository<TurSNSite, String> {

	List<TurSNSite> findByCreatedBy(Sort name, String createdBy);

	Optional<TurSNSite> findByName(String name);

	Optional<TurSNSite> findByNameIgnoreCase(String name);

	/**
	 * T818 / §LIX.1 (Block BK) — returns just the site's GenAI binding, so the
	 * catalog copilot can read its plain planning columns
	 * ({@code copilotPlanningStrategy} / {@code copilotPlanningMaxPasses}) without
	 * loading the site and navigating a LAZY {@code @OneToOne} outside a session (the
	 * copilot retrieval path is not transactional). Deliberately selects the GenAI
	 * entity directly rather than the site: nothing here dereferences the GenAI's own
	 * LAZY associations, so no fetch join is needed.
	 */
	@Query("""
			SELECT genAi FROM TurSNSite s
			JOIN s.turSNSiteGenAi genAi
			WHERE LOWER(s.name) = LOWER(:name)
			""")
	Optional<com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi> findGenAiByNameIgnoreCase(
			@Param("name") String name);

	/**
	 * Loads a site with its <em>to-one</em> GenAI graph fetched in one query —
	 * {@code turSEInstance}, {@code turSNSiteGenAi}, and the GenAI's
	 * {@code turAIAgent} + {@code ragSeInstance}. Intended for the admin
	 * load-mutate-map paths (T488 / §XXVIII.3) that map the site to a DTO or
	 * build an insights prompt: the mapper/prompt-builder dereferences these
	 * associations, so fetching them up front avoids the N+1 round-trips a plain
	 * {@code findById} would incur inside the transaction. Only {@code @ManyToOne}/
	 * {@code @OneToOne} associations are fetched — the {@code @OneToMany}
	 * collections stay lazy (multiple-bag fetch would throw) and resolve through
	 * the open session of the calling {@code @Transactional} method.
	 */
	@Query("""
			SELECT s FROM TurSNSite s
			LEFT JOIN FETCH s.turSEInstance
			LEFT JOIN FETCH s.turSNSiteGenAi genAi
			LEFT JOIN FETCH genAi.turAIAgent
			LEFT JOIN FETCH genAi.ragSeInstance
			WHERE s.id = :id
			""")
	Optional<TurSNSite> findByIdWithGenAi(@Param("id") String id);

	/**
	 * Loads a site by name with its <em>to-one</em> GenAI graph fetched in one
	 * query — {@code turSNSiteGenAi} and the GenAI's {@code turAIAgent}. Mirrors
	 * {@link #findByIdWithGenAi(String)} for the RAG-gating path
	 * ({@link com.viglet.turing.persistence.adapter.sn.TurSNSiteRepositoryAdapter#hasRagEnabledForSiteName(String)}),
	 * which navigates {@code site → genAi → agent} to read the agent's
	 * {@code enabled}/{@code ragEnabled} flags. Both associations are
	 * {@code @ManyToOne}/{@code @OneToOne} LAZY, so fetching them up front keeps
	 * the navigation valid even when the caller has no open session (e.g. the
	 * startup structured-feed provisioning path).
	 */
	@Query("""
			SELECT s FROM TurSNSite s
			LEFT JOIN FETCH s.turSNSiteGenAi genAi
			LEFT JOIN FETCH genAi.turAIAgent
			WHERE s.name = :name
			""")
	Optional<TurSNSite> findByNameWithGenAi(@Param("name") String name);

	/**
	 * Case-insensitive twin of {@link #findByNameWithGenAi(String)} used to build
	 * the cached SN search read-model ({@code TurSNSiteSearchSnapshot}). The
	 * snapshot holds the {@link TurSNSite} entity across the cache boundary, so any
	 * association the search hot path dereferences must be initialized while the
	 * session is open — otherwise a cache hit navigates a detached LAZY proxy and
	 * throws {@code LazyInitializationException}. The T383 hybrid-ranking path reads
	 * {@code turSNSiteGenAi.snRankingMode} / {@code embeddingModelId}, so the
	 * {@code @OneToOne} GenAI (and its {@code turAIAgent}) are fetched up front.
	 * Matches {@link #findByNameIgnoreCase(String)}'s case handling so the snapshot
	 * cache key ({@code lower(siteName)}) and this lookup agree.
	 */
	@Query("""
			SELECT s FROM TurSNSite s
			LEFT JOIN FETCH s.turSNSiteGenAi genAi
			LEFT JOIN FETCH genAi.turAIAgent
			WHERE LOWER(s.name) = LOWER(:name)
			""")
	Optional<TurSNSite> findByNameIgnoreCaseWithGenAi(@Param("name") String name);

	/**
	 * Returns every site eagerly loaded with its locales, intended for the
	 * listing endpoint. Uses {@code LEFT JOIN FETCH} to avoid the N+1 pattern
	 * that occurs when {@code TurSNSite.turSNSiteLocales} is mapped after the
	 * Hibernate session closes. Only the locale association is fetched — all
	 * other {@code @OneToMany} collections remain lazy and untouched.
	 */
	@Query("SELECT DISTINCT s FROM TurSNSite s LEFT JOIN FETCH s.turSNSiteLocales ORDER BY LOWER(s.name)")
	List<TurSNSite> findAllForListing();

	// T262 / §XIV.2.6 — findAllForListingByCreatedBy (the legacy createdBy
	// pseudo-tenant filter) is retired; isolation is the Hibernate @TenantId
	// discriminator, applied automatically to findAllForListing().
}
