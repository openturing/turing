package com.viglet.turing.persistence.repository.ml;

import com.viglet.turing.persistence.model.ml.TurMLModel;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurMLModelRepository extends JpaRepository<TurMLModel, Integer> {

	List<TurMLModel> findAll();

	TurMLModel findById(int id);
	
	TurMLModel save(TurMLModel turMLModel);

	void delete(TurMLModel turMLModel);
}
