package com.viglet.turing.persistence.repository.system;

import com.viglet.turing.persistence.model.system.TurLocale;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TurLocaleRepository extends JpaRepository<TurLocale, String> {

	List<TurLocale> findAll();

	TurLocale findByInitials(String initials);

	TurLocale save(TurLocale turLocale);

	void delete(TurLocale turConfigVar);
}
