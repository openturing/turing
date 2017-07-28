package com.viglet.turing.persistence.service;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.TurNLPVendor;
import com.viglet.turing.persistence.model.TurNLPVendorEntity;

public class TurNLPVendorEntityService extends TurBaseService {
	public void save(TurNLPVendorEntity turNLPVendorEntity) {
		if (turNLPVendorEntity.getTurEntity() != null) {
			turNLPVendorEntity.setTurEntity(em.merge(turNLPVendorEntity.getTurEntity()));
		}
		if (turNLPVendorEntity.getTurNLPVendor() != null) {
			turNLPVendorEntity.setTurNLPVendor(em.merge(turNLPVendorEntity.getTurNLPVendor()));
		}
		em.getTransaction().begin();
		em.persist(turNLPVendorEntity);
		em.getTransaction().commit();
	}

	public List<TurNLPVendorEntity> listAll() {
		TypedQuery<TurNLPVendorEntity> q = em.createNamedQuery("TurNLPVendorEntity.findAll", TurNLPVendorEntity.class);
		return q.getResultList();
	}

	public TurNLPVendorEntity get(String nlpVendorEntityId) {
		return em.find(TurNLPVendorEntity.class, nlpVendorEntityId);
	}

	public List<TurNLPVendorEntity> findByNLPVendor(TurNLPVendor turNLPVendor) {
		try {
			TypedQuery<TurNLPVendorEntity> q = em
					.createQuery("SELECT nve FROM TurNLPVendorEntity nve where nve.turNLPVendor = :turNLPVendor ",
							TurNLPVendorEntity.class)
					.setParameter("turNLPVendor", turNLPVendor);
			return q.getResultList();
		} catch (NoResultException e) {
			return null;
		}
	}

	public boolean delete(int nlpVendorEntityId) {
		TurNLPVendorEntity turNLPVendorEntity = em.find(TurNLPVendorEntity.class, nlpVendorEntityId);
		em.getTransaction().begin();
		em.remove(turNLPVendorEntity);
		em.getTransaction().commit();
		return true;
	}
}
