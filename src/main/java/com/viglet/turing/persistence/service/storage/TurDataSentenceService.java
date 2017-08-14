package com.viglet.turing.persistence.service.storage;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.storage.TurDataSentence;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurDataSentenceService extends TurBaseService {
	public void save(TurDataSentence turDataSentence) {
		em.getTransaction().begin();
		em.persist(turDataSentence);
		em.getTransaction().commit();
	}

	public List<TurDataSentence> listAll() {
		TypedQuery<TurDataSentence> q = em.createNamedQuery("TurDataSentence.findAll", TurDataSentence.class);
		return q.getResultList();
	}

	public TurDataSentence get(String dataId) {
		return em.find(TurDataSentence.class, dataId);
	}

	public boolean delete(int dataId) {
		TurDataSentence turDataSentence = em.find(TurDataSentence.class, dataId);
		em.getTransaction().begin();
		em.remove(turDataSentence);
		em.getTransaction().commit();
		return true;
	}
	
}
