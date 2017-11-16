package com.viglet.turing.persistence.repository.sn;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurSNSiteFieldExtRepository extends JpaRepository<TurSNSiteFieldExt, Integer> {

	List<TurSNSiteFieldExt> findAll();

	TurSNSiteFieldExt findById(int id);

	List<TurSNSiteFieldExt> findByTurSNSite(TurSNSite turSNSite);

	List<TurSNSiteFieldExt> findByTurSNSiteAndEnabled(TurSNSite turSNSite, int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndFacetAndEnabled(TurSNSite turSNSite, int facet, int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndHlAndEnabled(TurSNSite turSNSite, int hl, int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndMltAndEnabled(TurSNSite turSNSite, int mlt, int enabled);

	List<TurSNSiteFieldExt> findByTurSNSiteAndRequiredAndEnabled(TurSNSite turSNSite, int required, int enabled);

	TurSNSiteFieldExt save(TurSNSiteFieldExt turSNSiteFieldExt);

	void delete(TurSNSiteFieldExt turSNSiteFieldExt);

	@Modifying
	@Query("delete from TurSNSiteFieldExt ssfe where ssfe.id = ?1")
	void delete(int turSnSiteFieldId);
}
