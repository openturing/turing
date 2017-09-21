package com.viglet.turing.persistence.repository.nlp.term;

import com.viglet.turing.persistence.model.nlp.term.TurTerm;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurTermRepository extends JpaRepository<TurTerm, Integer> {

	List<TurTerm> findAll();
	
	TurTerm findOneByIdCustom(String idCustom);
	
	TurTerm findById(int id);

	TurTerm save(TurTerm turTerm);

	void delete(TurTerm turTerm);
}
