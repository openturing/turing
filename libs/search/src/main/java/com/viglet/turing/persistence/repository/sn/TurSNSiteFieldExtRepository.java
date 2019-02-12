package com.viglet.turing.persistence.repository.sn;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;

import java.util.List;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurSNSiteFieldExtRepository extends JpaRepository<TurSNSiteFieldExt, Integer> {

	List<TurSNSiteFieldExt> findAll();

	TurSNSiteFieldExt findById(int id);

	@Cacheable("turSNSiteFieldExtfindByTurSNSite")
	List<TurSNSiteFieldExt> findByTurSNSite(TurSNSite turSNSite);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndEnabled(TurSNSite turSNSite, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndFacetAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndFacetAndEnabled(TurSNSite turSNSite, int facet, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndHlAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndHlAndEnabled(TurSNSite turSNSite, int hl, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndMltAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndMltAndEnabled(TurSNSite turSNSite, int mlt, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndRequiredAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndRequiredAndEnabled(TurSNSite turSNSite, int required, int enabled);

	@Cacheable("turSNSiteFieldExtfindByTurSNSiteAndNlpAndEnabled")
	List<TurSNSiteFieldExt> findByTurSNSiteAndNlpAndEnabled(TurSNSite turSNSite, int nlp, int enabled);

	@CacheEvict(value = { "turSNSiteFieldExtfindByTurSNSite", "turSNSiteFieldExtfindByTurSNSiteAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndFacetAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndHlAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndMltAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndRequiredAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndNlpAndEnabled" }, allEntries = true)
	TurSNSiteFieldExt save(TurSNSiteFieldExt turSNSiteFieldExt);

	@CacheEvict(value = { "turSNSiteFieldExtfindByTurSNSite", "turSNSiteFieldExtfindByTurSNSiteAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndFacetAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndHlAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndMltAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndRequiredAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndNlpAndEnabled" }, allEntries = true)
	void delete(TurSNSiteFieldExt turSNSiteFieldExt);

	@Modifying
	@Query("delete from TurSNSiteFieldExt ssfe where ssfe.id = ?1")
	@CacheEvict(value = { "turSNSiteFieldExtfindByTurSNSite", "turSNSiteFieldExtfindByTurSNSiteAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndFacetAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndHlAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndMltAndEnabled", "turSNSiteFieldExtfindByTurSNSiteAndRequiredAndEnabled",
			"turSNSiteFieldExtfindByTurSNSiteAndNlpAndEnabled" }, allEntries = true)
	void delete(int turSnSiteFieldId);
}
