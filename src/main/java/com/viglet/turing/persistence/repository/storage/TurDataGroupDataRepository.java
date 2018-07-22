package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupData;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurDataGroupDataRepository extends JpaRepository<TurDataGroupData, Integer> {

	List<TurDataGroupData> findAll();

	TurDataGroupData findById(int id);
	
	List<TurDataGroupData> findByTurDataGroup(TurDataGroup turDataGroup);
	
	TurDataGroupData save(TurDataGroupData turDataGroupData);

	@Modifying
	@Query("delete from  TurDataGroupData dgd where dgd.id = ?1")
	void delete(int id);
}
