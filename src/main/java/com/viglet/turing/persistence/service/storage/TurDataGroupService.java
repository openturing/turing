package com.viglet.turing.persistence.service.storage;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurDataGroupService extends TurBaseService {
	public void save(TurDataGroup turDataGroup) {
		em.getTransaction().begin();
		em.persist(turDataGroup);
		em.getTransaction().commit();
	}

	public List<TurDataGroup> listAll() {
		TypedQuery<TurDataGroup> q = em.createNamedQuery("TurDataGroup.findAll", TurDataGroup.class);
		return q.getResultList();
	}

	public TurDataGroup get(int dataGroupId) {
		return em.find(TurDataGroup.class, dataGroupId);
	}

	public boolean delete(int dataGroupId) {
		TurDataGroup turDataGroup = em.find(TurDataGroup.class, dataGroupId);
		em.getTransaction().begin();
		em.remove(turDataGroup);
		em.getTransaction().commit();
		return true;
	}
	
}
