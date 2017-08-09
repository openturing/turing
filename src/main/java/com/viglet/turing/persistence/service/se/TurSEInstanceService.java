package com.viglet.turing.persistence.service.se;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurSEInstanceService extends TurBaseService {

	public void save(TurSEInstance turSEInstance) {
		if (turSEInstance.getTurSEVendor() != null) {
			turSEInstance.setTurSEVendor(em.merge(turSEInstance.getTurSEVendor()));
		}
		em.getTransaction().begin();
		em.persist(turSEInstance);
		em.getTransaction().commit();
	}

	public List<TurSEInstance> listAll() {
		TypedQuery<TurSEInstance> q = em.createNamedQuery("TurSEInstance.findAll", TurSEInstance.class);
		return q.getResultList();
	}

	public TurSEInstance get(int seInstanceId) {
		return em.find(TurSEInstance.class, seInstanceId);
	}

	public TurSEInstance getSEDefault() {
		TypedQuery<TurSEInstance> q = em
				.createQuery("SELECT ni FROM TurSEInstance ni WHERE ni.selected = :selected ", TurSEInstance.class)
				.setParameter("selected", 1);

		TurSEInstance turSEInstance = q.getSingleResult();
		return turSEInstance;
	}

	public boolean delete(int seInstanceId) {
		TurSEInstance turSEInstance = em.find(TurSEInstance.class, seInstanceId);
		em.getTransaction().begin();
		em.remove(turSEInstance);
		em.getTransaction().commit();
		return true;
	}

}
