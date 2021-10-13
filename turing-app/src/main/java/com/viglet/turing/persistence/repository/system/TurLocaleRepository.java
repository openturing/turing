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

import com.viglet.turing.persistence.model.system.TurLocale;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurLocaleRepository extends JpaRepository<TurLocale, String> {

	static final String EN_US = "en_US";
	static final String EN_GB = "en_GB";
	static final String PT_BR = "pt_BR";
	static final String CA = "ca";

	@Cacheable("turLocalefindAll")
	List<TurLocale> findAll();

	@Cacheable("turLocalefindByInitials")
	TurLocale findByInitials(String initials);

	@CacheEvict(value = { "turLocalefindAll", "turLocalefindByInitials" }, allEntries = true)
	<S extends TurLocale> S save(TurLocale turLocale);

	void delete(TurLocale turConfigVar);

	@Modifying
	@Query("delete from  TurLocale l where l.id = ?1")
	@CacheEvict(value = { "turLocalefindAll", "turLocalefindByInitials" }, allEntries = true)
	void delete(String initials);
}
