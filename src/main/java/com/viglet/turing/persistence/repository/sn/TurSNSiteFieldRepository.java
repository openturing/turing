package com.viglet.turing.persistence.repository.sn;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurSNSiteFieldRepository extends JpaRepository<TurSNSiteField, Integer> {

	List<TurSNSiteField> findAll();

	TurSNSiteField findById(int id);
	
	List<TurSNSiteField> findByTurSNSite(TurSNSite turSNSite);
	
	TurSNSiteField save(TurSNSiteField turSNSiteField);

	void delete(TurSNSiteField turSNSiteField);
	
	@Modifying
	@Query("delete from TurSNSiteField ssf where ssf.id = ?1")
	void delete(int turSnSiteFieldId);
}
