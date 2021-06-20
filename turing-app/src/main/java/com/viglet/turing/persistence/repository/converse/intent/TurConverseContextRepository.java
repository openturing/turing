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

package com.viglet.turing.persistence.repository.converse.intent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.converse.TurConverseAgent;
import com.viglet.turing.persistence.model.converse.intent.TurConverseContext;

public interface TurConverseContextRepository extends JpaRepository<TurConverseContext, String> {

	List<TurConverseContext> findAll();

	Set<TurConverseContext> findByIntentInputs_Id(String intentId);
	
	Set<TurConverseContext> findByIntentOutputs_Id(String intentId);
	
	Set<TurConverseContext> findByAgent(TurConverseAgent agent);
	
	Set<TurConverseContext> findByAgentAndText(TurConverseAgent agent, String text);
	
	Optional<TurConverseContext> findById(String id);

	TurConverseContext save(TurConverseContext turConverseContext);

	@Modifying
	@Query("delete from  TurConverseContext cc where cc.id = ?1")
	void delete(String id);
}
