/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.persistence.repository.sn;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurSNSiteFieldRepository extends JpaRepository<TurSNSiteField, String> {

	List<TurSNSiteField> findAll();

	@Cacheable("turSNSiteFieldfindById")
	Optional<TurSNSiteField> findById(String id);

	@Cacheable("turSNSiteFieldfindByTurSNSite")
	List<TurSNSiteField> findByTurSNSite(TurSNSite turSNSite);

	@SuppressWarnings("unchecked")
	@CacheEvict(value = { "turSNSiteFieldfindById", "turSNSiteFieldfindByTurSNSite" }, allEntries = true)
	TurSNSiteField save(TurSNSiteField turSNSiteField);

	@CacheEvict(value = { "turSNSiteFieldfindById", "turSNSiteFieldfindByTurSNSite" }, allEntries = true)
	void delete(TurSNSiteField turSNSiteField);

	@Modifying
	@Query("delete from TurSNSiteField ssf where ssf.id = ?1")
	@CacheEvict(value = { "turSNSiteFieldfindById", "turSNSiteFieldfindByTurSNSite" }, allEntries = true)
	void delete(String turSnSiteFieldId);
}
