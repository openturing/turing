package com.viglet.turing.persistence.repository.ml;

import com.viglet.turing.persistence.model.ml.TurMLCategory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurMLCategoryRepository extends JpaRepository<TurMLCategory, Integer> {

	List<TurMLCategory> findAll();

	TurMLCategory findById(int id);
	
	TurMLCategory save(TurMLCategory turMLCategory);

	void delete(TurMLCategory turMLCategory);
}
