package com.viglet.turing.persistence.repository.sn;

import com.viglet.turing.persistence.model.sn.TurSNSite;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurSNSiteRepository extends JpaRepository<TurSNSite, Integer> {

	List<TurSNSite> findAll();

	TurSNSite findById(int id);
	
	TurSNSite findByName(String name);

	TurSNSite save(TurSNSite turSNSite);

	void delete(TurSNSite turSNSite);
}
