package com.viglet.turing.persistence.repository.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPVendor;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurNLPVendorRepository extends JpaRepository<TurNLPVendor, String> {

	List<TurNLPVendor> findAll();

	TurNLPVendor findById(String id);

	TurNLPVendor save(TurNLPVendor turNLPVendor);

	void delete(TurNLPVendor turNLPVendorEntity);
}
