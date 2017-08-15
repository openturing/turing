package com.viglet.turing.persistence.service.nlp.term;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.term.TurTermRelationFrom;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurTermRelationFromService extends TurBaseService {
	public void save(TurTermRelationFrom turTermRelationFrom) {
		em.getTransaction().begin();
		em.persist(turTermRelationFrom);
		em.getTransaction().commit();
	}

	public List<TurTermRelationFrom> listAll() {
		TypedQuery<TurTermRelationFrom> q = em.createNamedQuery("TurTermRelationFrom.findAll", TurTermRelationFrom.class);
		return q.getResultList();
	}

	public TurTermRelationFrom get(String turTermRelationFromId) {
		return em.find(TurTermRelationFrom.class, turTermRelationFromId);
	}

	public boolean delete(int turTermRelationFromId) {
		TurTermRelationFrom turTermRelationFrom = em.find(TurTermRelationFrom.class, turTermRelationFromId);
		em.getTransaction().begin();
		em.remove(turTermRelationFrom);
		em.getTransaction().commit();
		return true;
	}
	
}
