package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupCategory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurDataGroupCategoryRepository extends JpaRepository<TurDataGroupCategory, Integer> {

	List<TurDataGroupCategory> findAll();

	TurDataGroupCategory findById(int id);

	List<TurDataGroupCategory> findByTurDataGroup(TurDataGroup turDataGroup);

	TurDataGroupCategory save(TurDataGroupCategory turDataGroupCategory);

	@Modifying
	@Query("delete from  TurDataGroupCategory dgc where dgc.id = ?1")
	void delete(int id);
}
