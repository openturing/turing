package com.viglet.turing.persistence.repository.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPFeature;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurNLPFeatureRepository extends JpaRepository<TurNLPFeature, Integer> {

	List<TurNLPFeature> findAll();

	TurNLPFeature findById(int id);

	TurNLPFeature save(TurNLPFeature turNLPFeature);

	void delete(TurNLPFeature turNLPFeature);
}
