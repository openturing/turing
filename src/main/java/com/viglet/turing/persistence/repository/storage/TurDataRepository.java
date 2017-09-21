package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurData;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurDataRepository extends JpaRepository<TurData, Integer> {

	List<TurData> findAll();

	TurData findById(int id);

	TurData save(TurData turData);

	void delete(TurData turData);
}
