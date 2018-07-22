package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurDataGroupRepository extends JpaRepository<TurDataGroup, Integer> {

	List<TurDataGroup> findAll();

	TurDataGroup findById(int id);

	TurDataGroup save(TurDataGroup turDataGroup);

	void delete(TurDataGroup turDataGroup);
	
	@Modifying
	@Query("delete from TurDataGroup dg where dg.id = ?1")
	void delete(int id);
}
