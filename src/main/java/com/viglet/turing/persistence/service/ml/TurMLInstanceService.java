package com.viglet.turing.persistence.service.ml;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.ml.TurMLInstance;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurMLInstanceService extends TurBaseService {

	public void save(TurMLInstance turMLInstance) {
		if (turMLInstance.getTurMLVendor() != null) {
			turMLInstance.setTurMLVendor(em.merge(turMLInstance.getTurMLVendor()));
		}
		em.getTransaction().begin();
		em.persist(turMLInstance);
		em.getTransaction().commit();
	}

	public List<TurMLInstance> listAll() {
		TypedQuery<TurMLInstance> q = em.createNamedQuery("TurMLInstance.findAll", TurMLInstance.class);
		return q.getResultList();
	}

	public TurMLInstance get(int mlInstanceId) {
		return em.find(TurMLInstance.class, mlInstanceId);
	}

	public TurMLInstance getMLDefault() {
		TypedQuery<TurMLInstance> q = em
				.createQuery("SELECT ni FROM TurMLInstance ni WHERE ni.selected = :selected ", TurMLInstance.class)
				.setParameter("selected", 1);

		TurMLInstance turMLInstance = q.getSingleResult();
		return turMLInstance;
	}

	public boolean delete(int mlInstanceId) {
		TurMLInstance turMLInstance = em.find(TurMLInstance.class, mlInstanceId);
		em.getTransaction().begin();
		em.remove(turMLInstance);
		em.getTransaction().commit();
		return true;
	}

}
