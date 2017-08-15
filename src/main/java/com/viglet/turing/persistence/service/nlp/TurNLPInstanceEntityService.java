package com.viglet.turing.persistence.service.nlp;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.TurEntity;
import com.viglet.turing.persistence.model.nlp.TurNLPInstance;
import com.viglet.turing.persistence.model.nlp.TurNLPInstanceEntity;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurNLPInstanceEntityService extends TurBaseService {
	public void save(TurNLPInstanceEntity turNLPInstanceEntity) {

		if (turNLPInstanceEntity.getTurNLPInstance() != null) {
			turNLPInstanceEntity.setTurNLPInstance(em.merge(turNLPInstanceEntity.getTurNLPInstance()));
		}

		if (turNLPInstanceEntity.getTurNLPInstance().getTurNLPVendor() != null) {
			turNLPInstanceEntity.getTurNLPInstance()
					.setTurNLPVendor(em.merge(turNLPInstanceEntity.getTurNLPInstance().getTurNLPVendor()));
		}
		if (turNLPInstanceEntity.getTurEntity() != null) {
			turNLPInstanceEntity.setTurEntity(em.merge(turNLPInstanceEntity.getTurEntity()));
		}

		em.getTransaction().begin();
		em.persist(turNLPInstanceEntity);
		em.getTransaction().commit();
	}

	public List<TurNLPInstanceEntity> listAll() {
		TypedQuery<TurNLPInstanceEntity> q = em.createNamedQuery("TurNLPInstanceEntity.findAll",
				TurNLPInstanceEntity.class);
		return q.getResultList();
	}

	public TurNLPInstanceEntity get(String nlpInstanceEntityId) {
		return em.find(TurNLPInstanceEntity.class, nlpInstanceEntityId);
	}

	public List<TurNLPInstanceEntity> findByNLPInstance(TurNLPInstance turNLPInstance) {
		try {
			TypedQuery<TurNLPInstanceEntity> q = em
					.createQuery("SELECT nie FROM TurNLPInstanceEntity nie where nie.turNLPInstance = :turNLPInstance ",
							TurNLPInstanceEntity.class)
					.setParameter("turNLPInstance", turNLPInstance);
			return q.getResultList();
		} catch (NoResultException e) {
			return null;
		}
	}

	public boolean delete(int nlpInstanceEntityId) {
		TurNLPInstanceEntity turNLPInstanceEntity = em.find(TurNLPInstanceEntity.class, nlpInstanceEntityId);
		em.getTransaction().begin();
		em.remove(turNLPInstanceEntity);
		em.getTransaction().commit();
		return true;
	}
}
