package com.viglet.turing.persistence.service;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.TurNLPInstance;
import com.viglet.turing.persistence.model.TurNLPInstanceEntity;
import com.viglet.turing.persistence.model.TurNLPVendorEntity;

public class TurNLPInstanceService extends TurBaseService {

	TurNLPVendorEntityService turNLPVendorEntityService = new TurNLPVendorEntityService();
	TurNLPInstanceEntityService turNLPInstanceEntityService = new TurNLPInstanceEntityService();

	public void save(TurNLPInstance turNLPInstance) {
		if (turNLPInstance.getTurNLPVendor() != null) {
			turNLPInstance.setTurNLPVendor(em.merge(turNLPInstance.getTurNLPVendor()));
		}
		em.getTransaction().begin();
		em.persist(turNLPInstance);
		em.getTransaction().commit();

		this.copyEntitiesFromVendorToInstance(turNLPInstance);
	}

	public List<TurNLPInstance> listAll() {
		TypedQuery<TurNLPInstance> q = em.createNamedQuery("TurNLPInstance.findAll", TurNLPInstance.class);
		return q.getResultList();
	}

	public TurNLPInstance get(int nlpInstanceId) {
		return em.find(TurNLPInstance.class, nlpInstanceId);
	}

	public TurNLPInstance getNLPDefault() {
		TypedQuery<TurNLPInstance> q = em
				.createQuery("SELECT ni FROM TurNLPInstance ni WHERE ni.selected = :selected ", TurNLPInstance.class)
				.setParameter("selected", 1);

		TurNLPInstance turNLPInstance = q.getSingleResult();
		return turNLPInstance;
	}

	public boolean delete(int nlpInstanceId) {
		TurNLPInstance turNLPInstance = em.find(TurNLPInstance.class, nlpInstanceId);
		em.getTransaction().begin();
		em.remove(turNLPInstance);
		em.getTransaction().commit();
		return true;
	}

	public void copyEntitiesFromVendorToInstance(TurNLPInstance turNLPInstance) {
		List<TurNLPVendorEntity> turNLPVendorEntities = turNLPVendorEntityService
				.findByNLPVendor(turNLPInstance.getTurNLPVendor());
		if (turNLPVendorEntities != null) {
			for (TurNLPVendorEntity turNLPVendorEntity : turNLPVendorEntities) {
				TurNLPInstanceEntity turNLPInstanceEntity = new TurNLPInstanceEntity();
				turNLPInstanceEntity.setName(turNLPVendorEntity.getName());
				turNLPInstanceEntity.setTurEntity(turNLPVendorEntity.getTurEntity());
				turNLPInstanceEntity.setTurNLPInstance(turNLPInstance);
				turNLPInstanceEntity.setEnabled(1);
				turNLPInstanceEntityService.save(turNLPInstanceEntity);
			}
		}
	}

}
