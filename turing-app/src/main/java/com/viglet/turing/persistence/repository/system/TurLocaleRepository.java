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

	@SuppressWarnings("unchecked")
	@CacheEvict(value = { "turLocalefindAll", "turLocalefindByInitials" }, allEntries = true)
	TurLocale save(TurLocale turLocale);

	void delete(TurLocale turConfigVar);

	@Modifying
	@Query("delete from  TurLocale l where l.id = ?1")
	@CacheEvict(value = { "turLocalefindAll", "turLocalefindByInitials" }, allEntries = true)
	void delete(String initials);
}
