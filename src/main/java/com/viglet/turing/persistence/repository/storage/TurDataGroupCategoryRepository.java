package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurDataGroupCategoryRepository extends JpaRepository<TurDataGroupCategory, Integer> {

	List<TurDataGroupCategory> findAll();

	TurDataGroupCategory findById(int id);

	List<TurDataGroupCategory> findByTurDataGroup(TurDataGroup turDataGroup);

	TurDataGroupCategory save(TurDataGroupCategory turDataGroupCategory);

	void delete(TurDataGroupCategory turDataGroupCategory);
}
