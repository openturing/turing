package com.viglet.turing.persistence.service.system;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurConfigVarService extends TurBaseService {
	public void save(TurConfigVar turConfigVar) {
		em.getTransaction().begin();
		em.persist(turConfigVar);
		em.getTransaction().commit();
	}

	public List<TurConfigVar> listAll() {
		TypedQuery<TurConfigVar> q = em.createNamedQuery("TurConfigVar.findAll", TurConfigVar.class);
		return q.getResultList();
	}

	public TurConfigVar get(String turConfigVarId) {
		return em.find(TurConfigVar.class, turConfigVarId);
	}

	public boolean delete(String turConfigVarId) {
		TurConfigVar turConfigVar = em.find(TurConfigVar.class, turConfigVarId);
		em.getTransaction().begin();
		em.remove(turConfigVar);
		em.getTransaction().commit();
		return true;
	}

}
