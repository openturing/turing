package com.viglet.turing.persistence.repository.sn;

import com.viglet.turing.persistence.model.sn.TurSNSite;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurSNSiteRepository extends JpaRepository<TurSNSite, String> {

	@Cacheable("turSNSitefindAll")
	List<TurSNSite> findAll();

	@Cacheable("turSNSitefindById")
	Optional<TurSNSite> findById(String id);
	
	@Cacheable("turSNSitefindByName")
	TurSNSite findByName(String name);

	TurSNSite save(TurSNSite turSNSite);

	void delete(TurSNSite turSNSite);
	
	@Modifying
	@Query("delete from  TurSNSite ss where ss.id = ?1")
	void delete(String id);
}
