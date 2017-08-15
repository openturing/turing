package com.viglet.turing.persistence.service.nlp.term;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.term.TurTermVariationLanguage;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurTermVariationLanguageService extends TurBaseService {
	public void save(TurTermVariationLanguage turTermVariationLanguage) {
		if (turTermVariationLanguage.getTurTermVariation() != null) {
			turTermVariationLanguage.setTurTermVariation(em.merge(turTermVariationLanguage.getTurTermVariation()));
		}
		
		if (turTermVariationLanguage.getTurTermVariation().getTurTerm() != null) {
			turTermVariationLanguage.getTurTermVariation().setTurTerm(em.merge(turTermVariationLanguage.getTurTermVariation().getTurTerm()));
		}
		
		if (turTermVariationLanguage.getTurTermVariation().getTurTerm().getTurNLPEntity() != null) {
			turTermVariationLanguage.getTurTermVariation().getTurTerm().setTurNLPEntity(em.merge(turTermVariationLanguage.getTurTermVariation().getTurTerm().getTurNLPEntity()));
		}
		
		
		if (turTermVariationLanguage.getTurTerm() != null) {
			turTermVariationLanguage.setTurTerm(em.merge(turTermVariationLanguage.getTurTerm()));
		}
		if (turTermVariationLanguage.getTurTerm().getTurNLPEntity() != null) {
			turTermVariationLanguage.getTurTerm()
					.setTurNLPEntity(em.merge(turTermVariationLanguage.getTurTerm().getTurNLPEntity()));
		}
		em.getTransaction().begin();
		em.persist(turTermVariationLanguage);
		em.getTransaction().commit();
	}

	public List<TurTermVariationLanguage> listAll() {
		TypedQuery<TurTermVariationLanguage> q = em.createNamedQuery("TurTermVariationLanguage.findAll",
				TurTermVariationLanguage.class);
		return q.getResultList();
	}

	public TurTermVariationLanguage get(String turTermVariationLanguageId) {
		return em.find(TurTermVariationLanguage.class, turTermVariationLanguageId);
	}

	public boolean delete(int turTermVariationLanguageId) {
		TurTermVariationLanguage turTermVariationLanguage = em.find(TurTermVariationLanguage.class,
				turTermVariationLanguageId);
		em.getTransaction().begin();
		em.remove(turTermVariationLanguage);
		em.getTransaction().commit();
		return true;
	}

}
