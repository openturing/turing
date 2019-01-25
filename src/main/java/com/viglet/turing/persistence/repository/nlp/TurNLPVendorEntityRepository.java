package com.viglet.turing.persistence.repository.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPVendor;
import com.viglet.turing.persistence.model.nlp.TurNLPVendorEntity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurNLPVendorEntityRepository extends JpaRepository<TurNLPVendorEntity, Integer> {

	List<TurNLPVendorEntity> findAll();
	List<TurNLPVendorEntity> findByTurNLPVendor(TurNLPVendor turNLPVendor);
	TurNLPVendorEntity findById(int id);

	TurNLPVendorEntity save(TurNLPVendorEntity turNLPVendorEntity);

	void delete(TurNLPVendorEntity turNLPVendorEntity);
}
