package com.viglet.turing.persistence.service.ml;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.ml.TurMLCategory;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurMLCategoryService extends TurBaseService {
	public void save(TurMLCategory turMLCategory) {
		em.getTransaction().begin();
		em.persist(turMLCategory);
		em.getTransaction().commit();
	}

	public List<TurMLCategory> listAll() {
		TypedQuery<TurMLCategory> q = em.createNamedQuery("TurMLCategory.findAll", TurMLCategory.class);
		return q.getResultList();
	}

	public TurMLCategory get(String mlCategoryId) {
		return em.find(TurMLCategory.class, mlCategoryId);
	}

	public boolean delete(int mlCategoryId) {
		TurMLCategory turMLCategory = em.find(TurMLCategory.class, mlCategoryId);
		em.getTransaction().begin();
		em.remove(turMLCategory);
		em.getTransaction().commit();
		return true;
	}

}
