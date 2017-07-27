package com.viglet.turing.persistence.service;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.TurNLPInstance;

public class TurNLPInstanceService extends TurBaseService {
	public void save(TurNLPInstance turNLPInstance) {
		if (turNLPInstance.getTurNLPVendor() != null) {
			turNLPInstance.setTurNLPVendor(em.merge(turNLPInstance.getTurNLPVendor()));
		}
		em.getTransaction().begin();
		em.persist(turNLPInstance);
		em.getTransaction().commit();
	}

	public List<TurNLPInstance> listAll() {
		TypedQuery<TurNLPInstance> q = em.createNamedQuery("TurNLPInstance.findAll", TurNLPInstance.class);
		return q.getResultList();
	}

	public TurNLPInstance get(int nlpInstanceId) {
		return em.find(TurNLPInstance.class, nlpInstanceId);
	}

	public boolean delete(int nlpInstanceId) {
		TurNLPInstance turNLPInstance = em.find(TurNLPInstance.class, nlpInstanceId);
		em.getTransaction().begin();
		em.remove(turNLPInstance);
		em.getTransaction().commit();
		return true;
	}
}
