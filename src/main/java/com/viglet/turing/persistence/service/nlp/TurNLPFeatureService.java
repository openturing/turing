package com.viglet.turing.persistence.service.nlp;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.TurNLPFeature;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurNLPFeatureService extends TurBaseService {
	public void save(TurNLPFeature turNLPFeature) {
		em.getTransaction().begin();
		em.persist(turNLPFeature);
		em.getTransaction().commit();
	}

	public List<TurNLPFeature> listAll() {
		TypedQuery<TurNLPFeature> q = em.createNamedQuery("TurNLPFeature.findAll", TurNLPFeature.class);
		return q.getResultList();
	}

	public TurNLPFeature get(int nlpFeatureId) {
		return em.find(TurNLPFeature.class, nlpFeatureId);
	}

	public boolean delete(int nlpFeatureId) {
		TurNLPFeature turNLPFeature = em.find(TurNLPFeature.class, nlpFeatureId);
		em.getTransaction().begin();
		em.remove(turNLPFeature);
		em.getTransaction().commit();
		return true;
	}
}
