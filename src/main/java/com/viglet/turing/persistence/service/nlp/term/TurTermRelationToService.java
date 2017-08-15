package com.viglet.turing.persistence.service.nlp.term;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.term.TurTermRelationTo;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurTermRelationToService extends TurBaseService {
	public void save(TurTermRelationTo turTermRelationTo) {
		if (turTermRelationTo.getTurTermRelationFrom() != null) {
			turTermRelationTo.setTurTermRelationFrom(em.merge(turTermRelationTo.getTurTermRelationFrom()));
		}

		if (turTermRelationTo.getTurTermRelationFrom().getTurTerm() != null) {
			turTermRelationTo.getTurTermRelationFrom()
					.setTurTerm(em.merge(turTermRelationTo.getTurTermRelationFrom().getTurTerm()));
		}

		if (turTermRelationTo.getTurTermRelationFrom().getTurTerm().getTurNLPEntity() != null) {
			turTermRelationTo.getTurTermRelationFrom().getTurTerm().setTurNLPEntity(
					em.merge(turTermRelationTo.getTurTermRelationFrom().getTurTerm().getTurNLPEntity()));
		}

		if (turTermRelationTo.getTurTerm() != null) {
			turTermRelationTo.setTurTerm(em.merge(turTermRelationTo.getTurTerm()));
		}
		if (turTermRelationTo.getTurTerm().getTurNLPEntity() != null) {
			turTermRelationTo.getTurTerm().setTurNLPEntity(em.merge(turTermRelationTo.getTurTerm().getTurNLPEntity()));
		}

		em.getTransaction().begin();
		em.persist(turTermRelationTo);
		em.getTransaction().commit();
	}

	public List<TurTermRelationTo> listAll() {
		TypedQuery<TurTermRelationTo> q = em.createNamedQuery("TurTermRelationTo.findAll", TurTermRelationTo.class);
		return q.getResultList();
	}

	public TurTermRelationTo get(String turTermRelationToId) {
		return em.find(TurTermRelationTo.class, turTermRelationToId);
	}

	public boolean delete(int turTermRelationToId) {
		TurTermRelationTo turTermRelationTo = em.find(TurTermRelationTo.class, turTermRelationToId);
		em.getTransaction().begin();
		em.remove(turTermRelationTo);
		em.getTransaction().commit();
		return true;
	}

}
