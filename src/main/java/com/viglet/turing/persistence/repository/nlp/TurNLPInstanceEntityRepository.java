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

package com.viglet.turing.persistence.repository.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurNLPInstanceEntityRepository
		extends JpaRepository<TurNLPInstanceEntity, String> {

	List<TurNLPInstanceEntity> findAll();

	List<TurNLPInstanceEntity> findByTurNLPInstanceAndLanguage(TurNLPInstance turNLPInstance, String Language);

	List<TurNLPInstanceEntity> findByTurNLPInstanceAndLanguageAndEnabled(TurNLPInstance turNLPInstance, String Language,
			int enabled);

	default List<TurNLPInstanceEntity> findByTurNLPInstance(TurNLPInstance turNLPInstance) {
		return findByTurNLPInstanceAndLanguage(turNLPInstance, turNLPInstance.getLanguage());
	}

	default List<TurNLPInstanceEntity> findByTurNLPInstanceAndEnabled(TurNLPInstance turNLPInstance, int enabled) {
		return findByTurNLPInstanceAndLanguageAndEnabled(turNLPInstance, turNLPInstance.getLanguage(), enabled);
	}

	Optional<TurNLPInstanceEntity> findById(String id);

	TurNLPInstanceEntity save(TurNLPInstanceEntity turNLPInstanceEntity);

	void delete(TurNLPInstanceEntity turNLPInstanceEntity);
}
