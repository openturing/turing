package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurDataGroupRepository extends JpaRepository<TurDataGroup, Integer> {

	List<TurDataGroup> findAll();

	TurDataGroup findById(int id);

	TurDataGroup save(TurDataGroup turDataGroup);

	void delete(TurDataGroup turDataGroup);
}
