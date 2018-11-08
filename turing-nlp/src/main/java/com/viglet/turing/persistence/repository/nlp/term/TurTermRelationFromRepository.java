package com.viglet.turing.persistence.repository.nlp.term;

import com.viglet.turing.persistence.model.nlp.term.TurTermRelationFrom;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurTermRelationFromRepository extends JpaRepository<TurTermRelationFrom, Integer> {

	List<TurTermRelationFrom> findAll();

	TurTermRelationFrom findById(int id);

	TurTermRelationFrom save(TurTermRelationFrom turTermRelationFrom);

	void delete(TurTermRelationFrom turTermRelationFrom);
}
