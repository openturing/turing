package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupData;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurDataGroupDataRepository extends JpaRepository<TurDataGroupData, Integer> {

	List<TurDataGroupData> findAll();

	TurDataGroupData findById(int id);
	
	List<TurDataGroupData> findByTurDataGroup(TurDataGroup turDataGroup);
	
	TurDataGroupData save(TurDataGroupData turDataGroupData);

	void delete(TurDataGroupData turDataGroupData);
}
