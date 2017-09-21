package com.viglet.turing.persistence.repository.se;

import com.viglet.turing.persistence.model.se.TurSEInstance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurSEInstanceRepository extends JpaRepository<TurSEInstance, Integer> {

	List<TurSEInstance> findAll();

	TurSEInstance findById(int id);

	TurSEInstance save(TurSEInstance turSEInstance);

	void delete(TurSEInstance turSEInstance);
}
