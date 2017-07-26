package com.viglet.turing.persistence.service;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.TurNLPSolution;

public class TurNLPSolutionService extends TurBaseService {
	public void save(TurNLPSolution turNLPSolution) {
		em.getTransaction().begin();
		em.persist(turNLPSolution);
		em.getTransaction().commit();
	}

	public List<TurNLPSolution> listAll() {
		TypedQuery<TurNLPSolution> q = em.createNamedQuery("TurNLPSolution.findAll", TurNLPSolution.class);
		return q.getResultList();
	}

	public TurNLPSolution get(int nlpSolutionId) {
		return em.find(TurNLPSolution.class, nlpSolutionId);
	}

	public boolean delete(int nlpSolutionId) {
		TurNLPSolution turNLPSolution = em.find(TurNLPSolution.class, nlpSolutionId);
		em.getTransaction().begin();
		em.remove(turNLPSolution);
		em.getTransaction().commit();
		return true;
	}
}
