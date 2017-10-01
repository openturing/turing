package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupModel;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurDataGroupModelRepository extends JpaRepository<TurDataGroupModel, Integer> {

	List<TurDataGroupModel> findAll();

	List<TurDataGroupModel> findByTurDataGroup(TurDataGroup turDataGroup);

	TurDataGroupModel findById(int id);

	TurDataGroupModel save(TurDataGroupModel turDataGroupModel);

	void delete(TurDataGroupModel turDataGroupModel);
}
