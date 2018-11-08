package com.viglet.turing.persistence.repository.nlp.term;

import com.viglet.turing.persistence.model.nlp.term.TurTermRelationTo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurTermRelationToRepository extends JpaRepository<TurTermRelationTo, Integer> {

	List<TurTermRelationTo> findAll();

	TurTermRelationTo findById(int id);

	TurTermRelationTo save(TurTermRelationTo turTermRelationTo);

	void delete(TurTermRelationTo turTermRelationTo);
}
