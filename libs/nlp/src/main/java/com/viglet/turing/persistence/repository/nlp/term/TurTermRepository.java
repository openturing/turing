package com.viglet.turing.persistence.repository.nlp.term;

import com.viglet.turing.persistence.model.nlp.term.TurTerm;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TurTermRepository extends JpaRepository<TurTerm, Integer> {

	List<TurTerm> findAll();
	
	TurTerm findOneByIdCustom(String idCustom);
	
	TurTerm findById(int id);

	TurTerm save(TurTerm turTerm);

	@Modifying
	@Query("delete from TurTerm t where t.id = ?1")
	void delete(int id);
}
