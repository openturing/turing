package com.viglet.turing.persistence.service.se;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.se.TurSEVendor;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurSEVendorService extends TurBaseService {
	public void save(TurSEVendor turSEVendor) {
		em.getTransaction().begin();
		em.persist(turSEVendor);
		em.getTransaction().commit();
	}

	public List<TurSEVendor> listAll() {
		TypedQuery<TurSEVendor> q = em.createNamedQuery("TurSEVendor.findAll", TurSEVendor.class);
		return q.getResultList();
	}

	public TurSEVendor get(String seVendorId) {
		return em.find(TurSEVendor.class, seVendorId);
	}

	public boolean delete(String seVendorId) {
		TurSEVendor turSEVendor = em.find(TurSEVendor.class, seVendorId);
		em.getTransaction().begin();
		em.remove(turSEVendor);
		em.getTransaction().commit();
		return true;
	}
	
}
