package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurDataGroupSentenceRepository extends JpaRepository<TurDataGroupSentence, Integer> {

	List<TurDataGroupSentence> findAll();

	List<TurDataGroupSentence> findByTurDataGroup(TurDataGroup turDataGroup);

	TurDataGroupSentence findById(int id);

	TurDataGroupSentence save(TurDataGroupSentence turDataGroupSentence);

	void delete(TurDataGroupSentence turDataGroupSentence);
}
