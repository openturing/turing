package com.viglet.turing.persistence.service;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.TurEntity;

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

	public TurEntity findByInternalName(String internalName) {
		try {
			TypedQuery<TurEntity> q = em
					.createQuery("SELECT e FROM TurEntity e where e.internalName = :internalName ", TurEntity.class)
					.setParameter("internalName", internalName);
			return q.getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}

	public boolean delete(int turEntityId) {
		TurEntity turEntity = em.find(TurEntity.class, turEntityId);
		em.getTransaction().begin();
		em.remove(turEntity);
		em.getTransaction().commit();
		return true;
	}
}
