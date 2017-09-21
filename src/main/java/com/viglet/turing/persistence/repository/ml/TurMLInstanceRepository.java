package com.viglet.turing.persistence.repository.ml;

import com.viglet.turing.persistence.model.ml.TurMLInstance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurMLInstanceRepository extends JpaRepository<TurMLInstance, Integer> {

	List<TurMLInstance> findAll();

	TurMLInstance findById(int id);
	
	TurMLInstance save(TurMLInstance turMLInstance);

	void delete(TurMLInstance turMLInstance);
}
