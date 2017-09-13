package com.viglet.turing.persistence.service.storage;

import java.util.List;

import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupData;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurDataGroupDataService extends TurBaseService {
	public void save(TurDataGroupData turDataGroupData) {
		if (turDataGroupData.getTurDataGroup() != null) {
			turDataGroupData.setTurDataGroup(em.merge(turDataGroupData.getTurDataGroup()));
		}
		
		if (turDataGroupData.getTurData() != null) {
			turDataGroupData.setTurData(em.merge(turDataGroupData.getTurData()));
		}
		em.getTransaction().begin();
		em.persist(turDataGroupData);
		em.getTransaction().commit();
	}

	public List<TurDataGroupData> listAll() {
		TypedQuery<TurDataGroupData> q = em.createNamedQuery("TurDataGroupData.findAll", TurDataGroupData.class);
		return q.getResultList();
	}
	
	public List<TurDataGroupData> findByDataGroup(TurDataGroup turDataGroup) {
		try {
			TypedQuery<TurDataGroupData> q = em
					.createQuery("SELECT dgd FROM TurDataGroupData dgd where dgd.turDataGroup = :turDataGroup ",
							TurDataGroupData.class)
					.setParameter("turDataGroup", turDataGroup);
			return q.getResultList();
		} catch (NoResultException e) {
			return null;
		}
	}
	public TurDataGroupData get(int dataGroupDataId) {
		return em.find(TurDataGroupData.class, dataGroupDataId);
	}

	public boolean delete(int dataGroupDataId) {
		TurDataGroupData turDataGroupData = em.find(TurDataGroupData.class, dataGroupDataId);
		em.getTransaction().begin();
		em.remove(turDataGroupData);
		em.getTransaction().commit();
		return true;
	}
	
	
}
