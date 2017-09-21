package com.viglet.turing.persistence.repository.ml;

import com.viglet.turing.persistence.model.ml.TurMLVendor;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurMLVendorRepository extends JpaRepository<TurMLVendor, String> {

	List<TurMLVendor> findAll();

	TurMLVendor findById(String id);
	
	TurMLVendor save(TurMLVendor turMLVendor);

	void delete(TurMLVendor turMLVendor);
}
