/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.persistence.repository.sn.spotlight;

import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlightTerm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * @author Alexandre Oliveira
 * @since 0.3.4
 */
public interface TurSNSiteSpotlightTermRepository extends JpaRepository<TurSNSiteSpotlightTerm, String> {

	@SuppressWarnings("unchecked")
	TurSNSiteSpotlightTerm save(TurSNSiteSpotlightTerm turSNSiteSpotlightTerm);

	void delete(TurSNSiteSpotlightTerm turSNSiteSpotlightTerm);

	@Modifying
	@Query("delete from  TurSNSiteSpotlightTerm ssst where ssst.id = ?1")
	void delete(String id);
}
