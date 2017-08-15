package com.viglet.turing.persistence.service.nlp.term;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.term.TurTermAttribute;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurTermAttributeService extends TurBaseService {
	public void save(TurTermAttribute turTermAttribute) {
		em.getTransaction().begin();
		em.persist(turTermAttribute);
		em.getTransaction().commit();
	}

	public List<TurTermAttribute> listAll() {
		TypedQuery<TurTermAttribute> q = em.createNamedQuery("TurTermAttribute.findAll", TurTermAttribute.class);
		return q.getResultList();
	}

	public TurTermAttribute get(String termAttributeId) {
		return em.find(TurTermAttribute.class, termAttributeId);
	}

	public boolean delete(int termAttributeId) {
		TurTermAttribute turTermAttribute = em.find(TurTermAttribute.class, termAttributeId);
		em.getTransaction().begin();
		em.remove(turTermAttribute);
		em.getTransaction().commit();
		return true;
	}
	
}
