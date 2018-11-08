package com.viglet.turing.persistence.repository.nlp.term;

import com.viglet.turing.persistence.model.nlp.term.TurTermAttribute;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurTermAttributeRepository extends JpaRepository<TurTermAttribute, Integer> {

	List<TurTermAttribute> findAll();

	TurTermAttribute findById(int id);

	TurTermAttribute save(TurTermAttribute turTermAttribute);

	void delete(TurTermAttribute turTermAttribute);
}
