/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.system.TurLocaleDomain;
import com.viglet.turing.persistence.model.system.TurLocale;
import com.viglet.turing.persistence.repository.system.TurLocaleRepository;

/** Unit tests for {@link TurLocaleRepositoryAdapter}. */
@ExtendWith(MockitoExtension.class)
class TurLocaleRepositoryAdapterTest {

    @Mock
    private TurLocaleRepository turLocaleRepository;

    private TurLocaleRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurLocaleRepositoryAdapter(turLocaleRepository,
                Mappers.getMapper(TurLocaleDomainMapper.class));
    }

    @Test
    void findByInitialsLooksUpByLocalePrimaryKey() {
        TurLocale entity = new TurLocale(Locale.US, "English (US)", "Inglês (EUA)");
        when(turLocaleRepository.findById(Locale.US)).thenReturn(Optional.of(entity));

        TurLocaleDomain domain = adapter.findByInitials(Locale.US).orElseThrow();

        assertThat(domain.initials()).isEqualTo(Locale.US);
        assertThat(domain.en()).isEqualTo("English (US)");
        assertThat(domain.pt()).isEqualTo("Inglês (EUA)");
    }

    @Test
    void findByInitialsEmpty() {
        when(turLocaleRepository.findById(Locale.JAPAN)).thenReturn(Optional.empty());
        assertThat(adapter.findByInitials(Locale.JAPAN)).isEmpty();
    }

    @Test
    void findAllReturnsMappedDomainList() {
        when(turLocaleRepository.findAll()).thenReturn(List.of(
                new TurLocale(Locale.US, "English (US)", "Inglês (EUA)"),
                new TurLocale(Locale.of("pt", "BR"), "Portuguese (BR)", "Português (BR)")));

        assertThat(adapter.findAll()).extracting(TurLocaleDomain::initials)
                .containsExactly(Locale.US, Locale.of("pt", "BR"));
    }
}
