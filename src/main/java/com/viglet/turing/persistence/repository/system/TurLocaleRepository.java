package com.viglet.turing.persistence.repository.system;

import com.viglet.turing.persistence.model.system.TurLocale;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurLocaleRepository extends JpaRepository<TurLocale, String> {

	static final String EN_US = "en_US";
	static final String EN_GB = "en_GB";
	static final String PT_BR = "pt_BR";
	
	List<TurLocale> findAll();

	TurLocale findByInitials(String initials);

	TurLocale save(TurLocale turLocale);

	void delete(TurLocale turConfigVar);
}
