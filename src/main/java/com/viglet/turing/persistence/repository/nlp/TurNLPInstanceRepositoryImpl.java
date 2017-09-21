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
				turNLPInstanceEntityRepository.save(turNLPInstanceEntity);
			}
		}
	}
}
