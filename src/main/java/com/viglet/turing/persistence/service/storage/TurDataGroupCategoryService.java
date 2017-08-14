package com.viglet.turing.persistence.service.storage;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurDataGroupCategoryService extends TurBaseService {
	public void save(TurDataGroupCategory turDataGroupCategory) {
		em.getTransaction().begin();
		em.persist(turDataGroupCategory);
		em.getTransaction().commit();
	}

	public List<TurDataGroupCategory> listAll() {
		TypedQuery<TurDataGroupCategory> q = em.createNamedQuery("TurDataGroupCategory.findAll", TurDataGroupCategory.class);
		return q.getResultList();
	}

	public TurDataGroupCategory get(String dataId) {
		return em.find(TurDataGroupCategory.class, dataId);
	}

	public boolean delete(int dataId) {
		TurDataGroupCategory turDataGroupCategory = em.find(TurDataGroupCategory.class, dataId);
		em.getTransaction().begin();
		em.remove(turDataGroupCategory);
		em.getTransaction().commit();
		return true;
	}
	
}
