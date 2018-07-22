package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupModel;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurDataGroupModelRepository extends JpaRepository<TurDataGroupModel, Integer> {

	List<TurDataGroupModel> findAll();

	List<TurDataGroupModel> findByTurDataGroup(TurDataGroup turDataGroup);

	TurDataGroupModel findById(int id);

	TurDataGroupModel save(TurDataGroupModel turDataGroupModel);

	@Modifying
	@Query("delete from  TurDataGroupModel dgm where dgm.id = ?1")
	void delete(int id);
}
