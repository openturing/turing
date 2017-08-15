package com.viglet.turing.persistence.service.nlp.term;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurTermService extends TurBaseService {
	public void save(TurTerm turTerm) {
		em.getTransaction().begin();
		em.persist(turTerm);
		em.getTransaction().commit();
	}

	public List<TurTerm> listAll() {
		TypedQuery<TurTerm> q = em.createNamedQuery("TurTerm.findAll", TurTerm.class);
		return q.getResultList();

	}

	public TurTerm get(int turTermId) {
		return em.find(TurTerm.class, turTermId);
	}

	public List<TurTerm> findByNLPEntity(TurNLPEntity turNLPEntity) {
		try {
			TypedQuery<TurTerm> q = em
					.createQuery("SELECT t FROM TurTerm t where t.turNLPEntity = :turNLPEntity ", TurTerm.class)
					.setParameter("turNLPInstance", turNLPEntity);
			return q.getResultList();
		} catch (NoResultException e) {
			return null;
		}
	}

	public boolean delete(int turTermId) {
		TurTerm turTerm = em.find(TurTerm.class, turTermId);
		em.getTransaction().begin();
		em.remove(turTerm);
		em.getTransaction().commit();
		return true;
	}
}
