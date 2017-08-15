package com.viglet.turing.persistence.service.nlp;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;
import com.viglet.turing.persistence.model.nlp.term.TurTerm;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationFrom;
import com.viglet.turing.persistence.model.nlp.term.TurTermRelationTo;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariation;
import com.viglet.turing.persistence.model.nlp.term.TurTermVariationLanguage;
import com.viglet.turing.persistence.service.TurBaseService;
import com.viglet.turing.persistence.service.nlp.term.TurTermService;

public class TurNLPEntityService extends TurBaseService {
	public void save(TurNLPEntity turNLPEntity) {
		em.getTransaction().begin();
		em.persist(turNLPEntity);
		em.getTransaction().commit();
	}

	public List<TurNLPEntity> listAll() {
		TypedQuery<TurNLPEntity> q = em.createNamedQuery("TurNLPEntity.findAll", TurNLPEntity.class);
		return q.getResultList();

	}

	public TurNLPEntity get(int entityId) {
		return em.find(TurNLPEntity.class, entityId);
	}
	
	public TurNLPEntity findByInternalName(String internalName) {
		try {
			TypedQuery<TurNLPEntity> q = em
					.createQuery("SELECT e FROM TurNLPEntity e where e.internalName = :internalName ", TurNLPEntity.class)
					.setParameter("internalName", internalName);
			return q.getSingleResult();
		} catch (NoResultException e) {
			return null;
		}
	}

	public boolean delete(int turNLPEntityId) {
		TurNLPEntity turNLPEntity = em.find(TurNLPEntity.class, turNLPEntityId);
		TurTermService turTermService = new TurTermService();

		for (Object termObject : turTermService.findByNLPEntity(turNLPEntity)) {
			TurTerm turTerm = (TurTerm) termObject;
			em.getTransaction().begin();
			for (TurTermRelationFrom turTermRelationFrom : turTerm.getTurTermRelationFroms()) {
				for (TurTermRelationTo turTermRelationTo : turTermRelationFrom.getTurTermRelationTos()) {
					em.remove(turTermRelationTo);
				}
				em.remove(turTermRelationFrom);
			}
			em.getTransaction().commit();
			em.getTransaction().begin();
			for (TurTermVariation turTermVariation : turTerm.getTurTermVariations()) {
				for (TurTermVariationLanguage turTermVariationLanguage : turTermVariation
						.getTurTermVariationLanguages()) {
					em.remove(turTermVariationLanguage);
				}
				em.remove(turTermVariation);
			}

			em.getTransaction().commit();

			em.getTransaction().begin();
			em.remove(turTerm);
			em.getTransaction().commit();
		}

		em.getTransaction().begin();
		em.remove(turNLPEntity);
		em.getTransaction().commit();
		return true;
	}
}
