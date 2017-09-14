package com.viglet.turing.persistence.service.storage;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurDataGroupSentenceService extends TurBaseService {
	public void save(TurDataGroupSentence turDataGroupSentence) {
		if (turDataGroupSentence.getTurData() != null) {
			turDataGroupSentence.setTurData(em.merge(turDataGroupSentence.getTurData()));
		}
		if (turDataGroupSentence.getTurMLCategory() != null) {
			turDataGroupSentence.setTurMLCategory(em.merge(turDataGroupSentence.getTurMLCategory()));
		}
		if (turDataGroupSentence.getTurDataGroup() != null) {
			turDataGroupSentence.setTurDataGroup(em.merge(turDataGroupSentence.getTurDataGroup()));
		}
		em.getTransaction().begin();
		em.persist(turDataGroupSentence);
		em.getTransaction().commit();
	}

	public List<TurDataGroupSentence> listAll() {
		TypedQuery<TurDataGroupSentence> q = em.createNamedQuery("TurDataGroupSentence.findAll",
				TurDataGroupSentence.class);
		return q.getResultList();
	}

	public List<TurDataGroupSentence> findByDataGroup(TurDataGroup turDataGroup) {
		try {
			TypedQuery<TurDataGroupSentence> q = em
					.createQuery("SELECT dgs FROM TurDataGroupSentence dgs where dgs.turDataGroup = :turDataGroup ",
							TurDataGroupSentence.class)
					.setParameter("turDataGroup", turDataGroup);
			return q.getResultList();
		} catch (NoResultException e) {
			return null;
		}
	}

	public TurDataGroupSentence get(int dataId) {
		return em.find(TurDataGroupSentence.class, dataId);
	}

	public boolean delete(int dataId) {
		TurDataGroupSentence turDataGroupSentence = em.find(TurDataGroupSentence.class, dataId);
		em.getTransaction().begin();
		em.remove(turDataGroupSentence);
		em.getTransaction().commit();
		return true;
	}

}
