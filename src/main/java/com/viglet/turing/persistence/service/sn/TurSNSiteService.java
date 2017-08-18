package com.viglet.turing.persistence.service.sn;

import java.util.List;

import javax.persistence.TypedQuery;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.service.TurBaseService;

public class TurSNSiteService extends TurBaseService {
	public void save(TurSNSite turSNSite) {
		if (turSNSite.getTurSEInstance() != null) {
			turSNSite.setTurSEInstance(em.merge(turSNSite.getTurSEInstance()));

		}

		if (turSNSite.getTurSEInstance().getTurSEVendor() != null) {
			turSNSite.getTurSEInstance().setTurSEVendor(em.merge(turSNSite.getTurSEInstance().getTurSEVendor()));
		}

		if (turSNSite.getTurNLPInstance() != null) {
			turSNSite.setTurNLPInstance(em.merge(turSNSite.getTurNLPInstance()));
		}

		if (turSNSite.getTurNLPInstance().getTurNLPVendor() != null) {
			turSNSite.getTurNLPInstance().setTurNLPVendor(em.merge(turSNSite.getTurNLPInstance().getTurNLPVendor()));
		}

		em.getTransaction().begin();
		em.persist(turSNSite);
		em.getTransaction().commit();
	}

	public List<TurSNSite> listAll() {
		TypedQuery<TurSNSite> q = em.createNamedQuery("TurSNSite.findAll", TurSNSite.class);
		return q.getResultList();
	}

	public TurSNSite get(int snSiteId) {
		return em.find(TurSNSite.class, snSiteId);
	}

	public boolean delete(int snSiteId) {
		TurSNSite turSNSite = em.find(TurSNSite.class, snSiteId);
		em.getTransaction().begin();
		em.remove(turSNSite);
		em.getTransaction().commit();
		return true;
	}

}
