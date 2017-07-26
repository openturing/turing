package com.viglet.turing.persistence.service;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.TurEntity;

public class TurEntityService extends TurBaseService {
	public void save(TurEntity turEntity) {
		em.getTransaction().begin();
		em.persist(turEntity);
		em.getTransaction().commit();
	}

	public List<TurEntity> listAll() {
		TypedQuery<TurEntity> q = em.createNamedQuery("TurEntity.findAll", TurEntity.class);
		return q.getResultList();
	}

	public TurEntity get(int entityId) {
		return em.find(TurEntity.class, entityId);
	}

	public boolean delete(int turEntityId) {
		TurEntity turEntity = em.find(TurEntity.class, turEntityId);
		em.getTransaction().begin();
		em.remove(turEntity);
		em.getTransaction().commit();
		return true;
	}
}
