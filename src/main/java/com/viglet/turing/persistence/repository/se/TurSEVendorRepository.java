package com.viglet.turing.persistence.repository.se;

import com.viglet.turing.persistence.model.se.TurSEVendor;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurSEVendorRepository extends JpaRepository<TurSEVendor, String> {
	
	@Cacheable("turSEVendorfindAll")
	List<TurSEVendor> findAll();
	
	@Cacheable("turSEVendorfindById")
	Optional<TurSEVendor> findById(String id);

	@CacheEvict(value = { "turSEVendorfindAll", "turSEVendorfindById" }, allEntries = true)
	TurSEVendor save(TurSEVendor turSEVendor);

	@Modifying
	@Query("delete from  TurSEVendor sv where sv.id = ?1")
	@CacheEvict(value = { "turSEVendorfindAll", "turSEVendorfindById" }, allEntries = true)
	void delete(String id);
}
