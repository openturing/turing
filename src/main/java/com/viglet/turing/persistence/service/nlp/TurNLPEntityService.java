package com.viglet.turing.persistence.service.nlp;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurNLPEntityService extends TurBaseService {
	public void save(TurNLPEntity turNLPEntity) {
		em.getTransaction().begin();
		em.persist(turNLPEntity);
		em.getTransaction().commit();
	}

	public List<TurNLPEntity> listAll() {
		TypedQuery<TurNLPEntity> q = em.createNamedQuery("TurNLPEntity.findAll", TurNLPEntity.class);
		return q.getResultList();
		
	}

	public TurNLPEntity get(int entityId) {
		return em.find(TurNLPEntity.class, entityId);
	}

	public TurNLPEntity findByInternalName(String internalName) {
		try {
			TypedQuery<TurNLPEntity> q = em
					.createQuery("SELECT e FROM TurNLPEntity e where e.internalName = :internalName ", TurNLPEntity.class)
					.setParameter("internalName", internalName);
			return q.getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}

	public boolean delete(int turNLPEntityId) {
		TurNLPEntity turNLPEntity = em.find(TurNLPEntity.class, turNLPEntityId);
		em.getTransaction().begin();
		em.remove(turNLPEntity);
		em.getTransaction().commit();
		return true;
	}
}
