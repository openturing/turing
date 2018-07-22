package com.viglet.turing.persistence.repository.ml;

import com.viglet.turing.persistence.model.ml.TurMLVendor;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurMLVendorRepository extends JpaRepository<TurMLVendor, String> {

	List<TurMLVendor> findAll();

	Optional<TurMLVendor> findById(String id);
	
	TurMLVendor save(TurMLVendor turMLVendor);

	@Modifying
	@Query("delete from TurMLVendor mv where mv.id = ?1")
	void delete(String id);
}
