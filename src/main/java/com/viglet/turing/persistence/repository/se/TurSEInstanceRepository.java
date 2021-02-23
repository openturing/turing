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

package com.viglet.turing.persistence.repository.se;

import com.viglet.turing.persistence.model.se.TurSEInstance;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurSEInstanceRepository extends JpaRepository<TurSEInstance, String> {

	@Cacheable("turSEInstancefindAll")
	List<TurSEInstance> findAll();

	@Cacheable("turSEInstancefindById")
	Optional<TurSEInstance> findById(String id);

	@CacheEvict(value = { "turSEInstancefindAll", "turSEInstancefindById" }, allEntries = true)
	TurSEInstance save(TurSEInstance turSEInstance);

	@Modifying
	@Query("delete from  TurSEInstance si where si.id = ?1")
	@CacheEvict(value = { "turSEInstancefindAll", "turSEInstancefindById" }, allEntries = true)
	void delete(String id);
}
