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

package com.viglet.turing.persistence.repository.system;

import com.viglet.turing.persistence.model.system.TurConfigVar;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurConfigVarRepository extends JpaRepository<TurConfigVar, String> {

	@Cacheable("turConfigVarfindAll")
	List<TurConfigVar> findAll();

	@Cacheable("turConfigVarfindById")
	Optional<TurConfigVar> findById(String id);

	@SuppressWarnings("unchecked")
	@CacheEvict(value = { "turConfigVarfindAll", "turConfigVarfindById" }, allEntries = true)
	TurConfigVar save(TurConfigVar turConfigVar);

	@CacheEvict(value = { "turConfigVarfindAll", "turConfigVarfindById" }, allEntries = true)
	void delete(TurConfigVar turConfigVar);
}
