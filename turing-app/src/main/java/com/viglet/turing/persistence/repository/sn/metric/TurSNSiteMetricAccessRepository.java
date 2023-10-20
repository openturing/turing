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
package com.viglet.turing.persistence.repository.sn.metric;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Date;
import java.util.List;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
public interface TurSNSiteMetricAccessRepository extends JpaRepository<TurSNSiteMetricAccess, String> {

	@SuppressWarnings("unchecked")
	@NotNull
	TurSNSiteMetricAccess save(@NotNull TurSNSiteMetricAccess turSNSiteLocale);

	@Query(value = "select distinct new com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm(sanatizedTerm, max(accessDate)) from "
			+ "TurSNSiteMetricAccess where turSNSite = ?1 and language = ?2 and userId = ?3 GROUP BY sanatizedTerm ORDER BY MAX(accessDate) DESC")
	List<TurSNSiteMetricAccessTerm> findLatestSearches(TurSNSite turSNSite,
			String language, String userId, Pageable pageable);
	@Query(value = "select distinct new com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm(sanatizedTerm, COUNT(sanatizedTerm), AVG(CAST(numFound as double))) from "
			+ "TurSNSiteMetricAccess where turSNSite = ?1 GROUP BY sanatizedTerm ORDER BY COUNT(sanatizedTerm) DESC")
	List<TurSNSiteMetricAccessTerm> topTerms(TurSNSite turSNSite, Pageable pageable);
	
	@Query(value = "select distinct new com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm(sanatizedTerm, COUNT(sanatizedTerm), AVG(CAST(numFound as double))) from "
			+ "TurSNSiteMetricAccess where turSNSite = ?1 AND accessDate BETWEEN ?2 AND ?3 GROUP BY sanatizedTerm ORDER BY COUNT(sanatizedTerm) DESC")
	List<TurSNSiteMetricAccessTerm> topTermsBetweenDates(TurSNSite turSNSite, Date startDate, Date endDate, Pageable pageable);
	
	@Query(value = "select COUNT(sanatizedTerm) from "
			+ "TurSNSiteMetricAccess where turSNSite = ?1")
	int countTerms(TurSNSite turSNSite);
	
	@Query(value = "select COUNT(sanatizedTerm) from "
			+ "TurSNSiteMetricAccess where turSNSite = ?1 AND accessDate BETWEEN ?2 AND ?3")
	int countTermsByPeriod(TurSNSite turSNSite, Date startDate, Date endDate);
	
	List<TurSNSiteMetricAccess> findByTurSNSiteAndLanguage(TurSNSite turSNSite, String language);

	List<TurSNSiteMetricAccess> findByTurSNSite(TurSNSite turSNSite);

	void delete(@NotNull TurSNSiteMetricAccess turSNSiteLocale);

	@Modifying
	@Query("delete from TurSNSiteMetricAccess ssma where ssma.id = ?1")
	void delete(String id);
}
