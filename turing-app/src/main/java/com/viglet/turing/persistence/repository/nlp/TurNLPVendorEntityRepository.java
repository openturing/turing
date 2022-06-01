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

import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurNLPVendorEntityRepository extends JpaRepository<TurNLPVendorEntity, String> {

	List<TurNLPVendorEntity> findByTurNLPVendor(TurNLPVendor turNLPVendor);
	
	List<TurNLPVendorEntity> findByTurNLPVendorAndTurNLPEntity_internalNameIn(TurNLPVendor turNLPVendor, List<String> entities);
	TurNLPVendorEntity findByTurNLPVendorAndTurNLPEntity_internalNameAndLanguage(TurNLPVendor turNLPVendor, String entityName, String language);

	void delete(TurNLPVendorEntity turNLPVendorEntity);
}
