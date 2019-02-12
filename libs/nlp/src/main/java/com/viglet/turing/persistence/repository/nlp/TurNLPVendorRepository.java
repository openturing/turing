package com.viglet.turing.persistence.repository.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPVendor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurNLPVendorRepository extends JpaRepository<TurNLPVendor, String> {

	List<TurNLPVendor> findAll();

	Optional<TurNLPVendor> findById(String id);

	TurNLPVendor save(TurNLPVendor turNLPVendor);

	@Modifying
	@Query("delete from  TurNLPVendor nv where nv.id = ?1")
	void delete(String id);
}
