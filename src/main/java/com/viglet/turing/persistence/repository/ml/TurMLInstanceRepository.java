package com.viglet.turing.persistence.repository.ml;

import com.viglet.turing.persistence.model.ml.TurMLInstance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurMLInstanceRepository extends JpaRepository<TurMLInstance, Integer> {

	List<TurMLInstance> findAll();

	TurMLInstance findById(int id);
	
	TurMLInstance save(TurMLInstance turMLInstance);

	@Modifying
	@Query("delete from TurMLInstance mi where mi.id = ?1")
	void delete(int id);
}
