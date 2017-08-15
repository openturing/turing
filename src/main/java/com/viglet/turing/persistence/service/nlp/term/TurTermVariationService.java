package com.viglet.turing.persistence.service.nlp.term;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.term.TurTermVariation;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurTermVariationService extends TurBaseService {
	public void save(TurTermVariation turTermVariation) {
		em.getTransaction().begin();
		em.persist(turTermVariation);
		em.getTransaction().commit();
	}

	public List<TurTermVariation> listAll() {
		TypedQuery<TurTermVariation> q = em.createNamedQuery("TurTermVariation.findAll", TurTermVariation.class);
		return q.getResultList();
	}

	public TurTermVariation get(String turTermVariationId) {
		return em.find(TurTermVariation.class, turTermVariationId);
	}

	public boolean delete(int turTermVariationId) {
		TurTermVariation turTermVariation = em.find(TurTermVariation.class, turTermVariationId);
		em.getTransaction().begin();
		em.remove(turTermVariation);
		em.getTransaction().commit();
		return true;
	}
	
}
