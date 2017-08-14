package com.viglet.turing.persistence.service.storage;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.storage.TurDataGroupData;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurDataGroupDataService extends TurBaseService {
	public void save(TurDataGroupData turDataGroupData) {
		em.getTransaction().begin();
		em.persist(turDataGroupData);
		em.getTransaction().commit();
	}

	public List<TurDataGroupData> listAll() {
		TypedQuery<TurDataGroupData> q = em.createNamedQuery("TurDataGroupData.findAll", TurDataGroupData.class);
		return q.getResultList();
	}

	public TurDataGroupData get(String dataId) {
		return em.find(TurDataGroupData.class, dataId);
	}

	public boolean delete(int dataId) {
		TurDataGroupData turDataGroupData = em.find(TurDataGroupData.class, dataId);
		em.getTransaction().begin();
		em.remove(turDataGroupData);
		em.getTransaction().commit();
		return true;
	}
	
}
