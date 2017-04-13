package com.viglet.turing.service;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;

import com.viglet.turing.persistence.model.VigService;


public class VigServiceUtil {
	final int TRUE = 1;
	final int FALSE = 1;
	final int NLP_TYPE = 2;
	final int SE_TYPE = 3;
	EntityManager em;
	
	public VigServiceUtil() {

		String PERSISTENCE_UNIT_NAME = "semantics-app";
		EntityManagerFactory factory;

		factory = Persistence.createEntityManagerFactory(PERSISTENCE_UNIT_NAME);
		em = factory.createEntityManager();

		
	}
	public int getNLPDefault(){
		Query queryService = em
				.createQuery(
						"SELECT s FROM VigService s WHERE s.type = :type and s.selected = :selected ")
				.setParameter("type", NLP_TYPE).setParameter("selected", TRUE);

		VigService vigService = (VigService) queryService.getSingleResult();
		return vigService.getId();
	}
	
	public int getSEDefault(){
		Query queryService = em
				.createQuery(
						"SELECT s FROM VigService s WHERE s.type = :type and s.selected = :selected ")
				.setParameter("type", SE_TYPE).setParameter("selected", TRUE);

		VigService vigService = (VigService) queryService.getSingleResult();
		return vigService.getId();
	}

}
