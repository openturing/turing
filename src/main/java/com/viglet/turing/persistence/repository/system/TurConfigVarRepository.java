package com.viglet.turing.persistence.repository.system;

import com.viglet.turing.persistence.model.system.TurConfigVar;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurConfigVarRepository extends JpaRepository<TurConfigVar, String> {

	List<TurConfigVar> findAll();

	TurConfigVar findById(String id);

	TurConfigVar save(TurConfigVar turConfigVar);

	void delete(TurConfigVar turConfigVar);
}
