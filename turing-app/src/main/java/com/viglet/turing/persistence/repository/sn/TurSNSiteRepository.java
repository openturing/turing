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

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurSNSiteRepository extends JpaRepository<TurSNSite, String> {

	@Cacheable("turSNSitefindAll")
	List<TurSNSite> findAll();

	@Cacheable("turSNSitefindById")
	Optional<TurSNSite> findById(String id);

	@Cacheable("turSNSitefindByName")
	TurSNSite findByName(String name);

	@SuppressWarnings("unchecked")
	@CacheEvict(value = { "turSNSitefindAll", "turSNSitefindById", "turSNSitefindByName" }, allEntries = true)
	TurSNSite save(TurSNSite turSNSite);

	@CacheEvict(value = { "turSNSitefindAll", "turSNSitefindById", "turSNSitefindByName" }, allEntries = true)
	void delete(TurSNSite turSNSite);

	@Modifying
	@Query("delete from  TurSNSite ss where ss.id = ?1")
	@CacheEvict(value = { "turSNSitefindAll", "turSNSitefindById", "turSNSitefindByName" }, allEntries = true)
	void delete(String id);
}
