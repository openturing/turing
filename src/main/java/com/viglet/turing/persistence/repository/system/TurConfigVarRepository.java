package com.viglet.turing.persistence.repository.system;

import com.viglet.turing.persistence.model.system.TurConfigVar;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurConfigVarRepository extends JpaRepository<TurConfigVar, String> {

	@Cacheable("turConfigVarfindAll")
	List<TurConfigVar> findAll();

	@Cacheable("turConfigVarfindById")
	Optional<TurConfigVar> findById(String id);

	TurConfigVar save(TurConfigVar turConfigVar);

	void delete(TurConfigVar turConfigVar);
}
