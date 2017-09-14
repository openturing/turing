package com.viglet.turing.persistence.service.storage;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurDataGroupCategoryService extends TurBaseService {
	public void save(TurDataGroupCategory turDataGroupCategory) {
		if (turDataGroupCategory.getTurDataGroup() != null) {
			turDataGroupCategory.setTurDataGroup(em.merge(turDataGroupCategory.getTurDataGroup()));
		}

		if (turDataGroupCategory.getTurMLCategory() != null) {
			turDataGroupCategory.setTurMLCategory(em.merge(turDataGroupCategory.getTurMLCategory()));
		}
		em.getTransaction().begin();
		em.persist(turDataGroupCategory);
		em.getTransaction().commit();
	}

	public List<TurDataGroupCategory> listAll() {
		TypedQuery<TurDataGroupCategory> q = em.createNamedQuery("TurDataGroupCategory.findAll",
				TurDataGroupCategory.class);
		return q.getResultList();
	}

	public List<TurDataGroupCategory> findByDataGroup(TurDataGroup turDataGroup) {
		try {
			TypedQuery<TurDataGroupCategory> q = em
					.createQuery("SELECT dgc FROM TurDataGroupCategory dgc where dgc.turDataGroup = :turDataGroup ",
							TurDataGroupCategory.class)
					.setParameter("turDataGroup", turDataGroup);
			return q.getResultList();
		} catch (NoResultException e) {
			return null;
		}
	}

	public TurDataGroupCategory get(int dataGroupCategoryId) {
		return em.find(TurDataGroupCategory.class, dataGroupCategoryId);
	}

	public boolean delete(int dataGroupCategoryId) {
		TurDataGroupCategory turDataGroupCategory = em.find(TurDataGroupCategory.class, dataGroupCategoryId);
		em.getTransaction().begin();
		em.remove(turDataGroupCategory);
		em.getTransaction().commit();
		return true;
	}

}
