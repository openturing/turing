package com.viglet.turing.persistence.service.ml;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.ml.TurMLModel;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurMLModelService extends TurBaseService {
	public void save(TurMLModel turMLModel) {
		em.getTransaction().begin();
		em.persist(turMLModel);
		em.getTransaction().commit();
	}

	public List<TurMLModel> listAll() {
		TypedQuery<TurMLModel> q = em.createNamedQuery("TurMLModel.findAll", TurMLModel.class);
		return q.getResultList();
	}

	public TurMLModel get(int mlModelId) {
		return em.find(TurMLModel.class, mlModelId);
	}

	public boolean delete(int mlModelId) {
		TurMLModel turMLModel = em.find(TurMLModel.class, mlModelId);
		em.getTransaction().begin();
		em.remove(turMLModel);
		em.getTransaction().commit();
		return true;
	}
	
}
