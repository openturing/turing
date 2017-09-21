package com.viglet.turing.persistence.repository.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurNLPInstanceRepository extends JpaRepository<TurNLPInstance, Integer> {

	List<TurNLPInstance> findAll();

	TurNLPInstance findById(int id);

	TurNLPInstance save(TurNLPInstance turNLPInstance);

	void delete(TurNLPInstance turNLPInstance);
}
