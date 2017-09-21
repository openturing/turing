package com.viglet.turing.persistence.repository.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPEntity;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurNLPEntityRepository extends JpaRepository<TurNLPEntity, Integer> {

	List<TurNLPEntity> findAll();

	TurNLPEntity findById(int id);

	TurNLPEntity findByInternalName(String internalName);
	
	TurNLPEntity findByName(String name);

	TurNLPEntity save(TurNLPEntity turNLPEntity);

	void delete(TurNLPEntity turNLPEntity);
}
