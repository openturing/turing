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

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;

@Component
@Transactional
class TurNLPInstanceRepositoryImpl implements TurNLPInstanceRepositoryCustom {

	@Autowired
	TurNLPInstanceRepository turNLPInstanceRepository;
	@Autowired
	private TurNLPVendorEntityRepository turNLPVendorEntityRepository;
	@Autowired
	private TurNLPInstanceEntityRepository turNLPInstanceEntityRepository;

	public TurNLPInstance saveAndAssocEntity(TurNLPInstance turNLPInstance) {
		TurNLPInstance turNLPInstanceEdit = this.turNLPInstanceRepository.save(turNLPInstance);
		this.copyEntitiesFromVendorToInstance(turNLPInstanceEdit);
		return turNLPInstanceEdit;
	}

	public void copyEntitiesFromVendorToInstance(TurNLPInstance turNLPInstance) {
		List<TurNLPVendorEntity> turNLPVendorEntities = turNLPVendorEntityRepository
				.findByTurNLPVendor(turNLPInstance.getTurNLPVendor());
		if (turNLPVendorEntities != null) {
			for (TurNLPVendorEntity turNLPVendorEntity : turNLPVendorEntities) {
				TurNLPInstanceEntity turNLPInstanceEntity = new TurNLPInstanceEntity();
				turNLPInstanceEntity.setName(turNLPVendorEntity.getName());
				turNLPInstanceEntity.setTurNLPEntity(turNLPVendorEntity.getTurNLPEntity());
				turNLPInstanceEntity.setTurNLPInstance(turNLPInstance);
				turNLPInstanceEntity.setEnabled(1);
				turNLPInstanceEntity.setLanguage(turNLPVendorEntity.getLanguage());
				turNLPInstanceEntityRepository.save(turNLPInstanceEntity);
			}
		}
	}
}
