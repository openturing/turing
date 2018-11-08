package com.viglet.turing.persistence.repository.nlp.term;

import com.viglet.turing.persistence.model.nlp.term.TurTermVariation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurTermVariationRepository extends JpaRepository<TurTermVariation, Integer> {

	List<TurTermVariation> findAll();

	TurTermVariation findById(int id);

	TurTermVariation save(TurTermVariation turTermVariation);

	void delete(TurTermVariation turTermVariation);
}
