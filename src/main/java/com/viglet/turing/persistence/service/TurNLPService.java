package com.viglet.turing.persistence.service;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.TurNLP;

public class TurNLPService extends TurBaseService {
	public void save(TurNLP turNLP) {
		em.getTransaction().begin();
		em.persist(turNLP);
		em.getTransaction().commit();
	}

	public List<TurNLP> listAll() {
		TypedQuery<TurNLP> q = em.createNamedQuery("TurNLP.findAll", TurNLP.class);
		return q.getResultList();
	}

	public TurNLP get(int nlpId) {
		return em.find(TurNLP.class, nlpId);
	}

	public boolean delete(int nlpId) {
		TurNLP turNLP = em.find(TurNLP.class, nlpId);
		em.getTransaction().begin();
		em.remove(turNLP);
		em.getTransaction().commit();
		return true;
	}
}
