package com.viglet.turing.persistence.service.storage;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.storage.TurData;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurDataService extends TurBaseService {
	public void save(TurData turData) {
		em.getTransaction().begin();
		em.persist(turData);
		em.getTransaction().commit();
	}

	public List<TurData> listAll() {
		TypedQuery<TurData> q = em.createNamedQuery("TurData.findAll", TurData.class);
		return q.getResultList();
	}

	public TurData get(int dataId) {
		return em.find(TurData.class, dataId);
	}

	public boolean delete(int dataId) {
		TurData turData = em.find(TurData.class, dataId);
		em.getTransaction().begin();
		em.remove(turData);
		em.getTransaction().commit();
		return true;
	}
	
}
