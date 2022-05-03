/*
 * Copyright (C) 2016-2022 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.persistence.repository.sn.metric;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
public interface TurSNSiteMetricAccessRepository extends JpaRepository<TurSNSiteMetricAccess, String> {

	@SuppressWarnings("unchecked")
	TurSNSiteMetricAccess save(TurSNSiteMetricAccess turSNSiteLocale);

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

	void delete(TurSNSiteMetricAccess turSNSiteLocale);

	@Modifying
	@Query("delete from TurSNSiteMetricAccess ssma where ssma.id = ?1")
	void delete(String id);
}
