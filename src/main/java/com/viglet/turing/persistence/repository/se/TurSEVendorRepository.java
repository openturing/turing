package com.viglet.turing.persistence.repository.se;

import com.viglet.turing.persistence.model.se.TurSEVendor;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurSEVendorRepository extends JpaRepository<TurSEVendor, String> {

	List<TurSEVendor> findAll();

	TurSEVendor findById(String id);

	TurSEVendor save(TurSEVendor turSEVendor);

	void delete(TurSEVendor turSEVendor);
}
