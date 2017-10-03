package com.viglet.turing.persistence.repository.storage;

import com.viglet.turing.persistence.model.storage.TurDataGroup;
import com.viglet.turing.persistence.model.storage.TurDataGroupSentence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurDataGroupSentenceRepository extends JpaRepository<TurDataGroupSentence, Integer> {

	List<TurDataGroupSentence> findAll();

	List<TurDataGroupSentence> findByTurDataGroup(TurDataGroup turDataGroup);

	TurDataGroupSentence findById(int id);

	TurDataGroupSentence save(TurDataGroupSentence turDataGroupSentence);

	void delete(TurDataGroupSentence turDataGroupSentence);
	
	@Modifying
	@Query("delete from TurDataGroupSentence ds where ds.id = ?1")
	void delete(int turDataGroupSentenceId);
}
