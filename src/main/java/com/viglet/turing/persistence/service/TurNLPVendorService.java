package com.viglet.turing.persistence.service;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.TurNLPVendor;

public class TurNLPVendorService extends TurBaseService {
	public void save(TurNLPVendor turNLPVendor) {
		em.getTransaction().begin();
		em.persist(turNLPVendor);
		em.getTransaction().commit();
	}

	public List<TurNLPVendor> listAll() {
		TypedQuery<TurNLPVendor> q = em.createNamedQuery("TurNLPVendor.findAll", TurNLPVendor.class);
		return q.getResultList();
	}

	public TurNLPVendor get(int nlpVendorId) {
		return em.find(TurNLPVendor.class, nlpVendorId);
	}

	public boolean delete(int nlpVendorId) {
		TurNLPVendor turNLPVendor = em.find(TurNLPVendor.class, nlpVendorId);
		em.getTransaction().begin();
		em.remove(turNLPVendor);
		em.getTransaction().commit();
		return true;
	}
}
