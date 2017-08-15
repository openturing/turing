package com.viglet.turing.persistence.service.ml;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.ml.TurMLVendor;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurMLVendorService extends TurBaseService {
	public void save(TurMLVendor turMLVendor) {
		em.getTransaction().begin();
		em.persist(turMLVendor);
		em.getTransaction().commit();
	}

	public List<TurMLVendor> listAll() {
		TypedQuery<TurMLVendor> q = em.createNamedQuery("TurMLVendor.findAll", TurMLVendor.class);
		return q.getResultList();
	}

	public TurMLVendor get(String mlVendorId) {
		return em.find(TurMLVendor.class, mlVendorId);
	}

	public boolean delete(String mlVendorId) {
		TurMLVendor turMLVendor = em.find(TurMLVendor.class, mlVendorId);
		em.getTransaction().begin();
		em.remove(turMLVendor);
		em.getTransaction().commit();
		return true;
	}
	
}
