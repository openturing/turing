package com.viglet.turing.persistence.repository.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurNLPInstanceRepository
		extends JpaRepository<TurNLPInstance, Integer>, TurNLPInstanceRepositoryCustom {

	List<TurNLPInstance> findAll();

	TurNLPInstance findById(int id);

	TurNLPInstance save(TurNLPInstance turNLPInstance);

	@Modifying
	@Query("delete from  TurNLPInstance ni where ni.id = ?1")
	void delete(int id);
}
