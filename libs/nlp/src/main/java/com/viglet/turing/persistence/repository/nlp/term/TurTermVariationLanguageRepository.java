package com.viglet.turing.persistence.repository.nlp.term;

import com.viglet.turing.persistence.model.nlp.term.TurTermVariationLanguage;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurTermVariationLanguageRepository extends JpaRepository<TurTermVariationLanguage, Integer> {

	List<TurTermVariationLanguage> findAll();

	TurTermVariationLanguage findById(int id);

	TurTermVariationLanguage save(TurTermVariationLanguage turTermVariationLanguage);

	void delete(TurTermVariationLanguage turTermVariationLanguage);
}
