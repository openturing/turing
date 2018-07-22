package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurData;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurDataRepository extends JpaRepository<TurData, Integer> {

	List<TurData> findAll();

	TurData findById(int id);

	TurData save(TurData turData);

	@Modifying
	@Query("delete from TurData d where d.id = ?1")
	void delete(int id);
}
