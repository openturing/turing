package com.viglet.turing.persistence.service.system;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurLocaleService extends TurBaseService {

	public void save(TurLocale turLocale) {
		em.getTransaction().begin();
		em.persist(turLocale);
		em.getTransaction().commit();
	}

	public List<TurLocale> listAll() {
		TypedQuery<TurLocale> q = em.createNamedQuery("TurLocale.findAll", TurLocale.class);
		return q.getResultList();
	}

	public TurLocale get(String turLocaleId) {
		return em.find(TurLocale.class, turLocaleId);
	}

	public boolean delete(String turLocaleId) {
		TurLocale turLocale = em.find(TurLocale.class, turLocaleId);
		em.getTransaction().begin();
		em.remove(turLocale);
		em.getTransaction().commit();
		return true;
	}

}
