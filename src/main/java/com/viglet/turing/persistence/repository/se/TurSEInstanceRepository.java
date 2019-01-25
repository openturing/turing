package com.viglet.turing.persistence.repository.se;

import com.viglet.turing.persistence.model.se.TurSEInstance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurSEInstanceRepository extends JpaRepository<TurSEInstance, Integer> {

	List<TurSEInstance> findAll();

	TurSEInstance findById(int id);

	TurSEInstance save(TurSEInstance turSEInstance);

	@Modifying
	@Query("delete from  TurSEInstance si where si.id = ?1")
	void delete(int id);
}
