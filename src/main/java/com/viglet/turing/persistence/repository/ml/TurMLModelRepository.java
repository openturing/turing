package com.viglet.turing.persistence.repository.ml;

import com.viglet.turing.persistence.model.ml.TurMLModel;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurMLModelRepository extends JpaRepository<TurMLModel, Integer> {

	List<TurMLModel> findAll();

	TurMLModel findById(int id);
	
	TurMLModel save(TurMLModel turMLModel);

	@Modifying
	@Query("delete from  TurMLModel mm where mm.id = ?1")
	void delete(int id);
}
