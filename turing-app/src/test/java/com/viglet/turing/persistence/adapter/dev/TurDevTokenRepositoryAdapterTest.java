/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.persistence.adapter.dev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.domain.dev.TurDevTokenDomain;
import com.viglet.turing.persistence.model.dev.token.TurDevToken;
import com.viglet.turing.persistence.repository.dev.token.TurDevTokenRepository;

/**
 * Unit tests for {@link TurDevTokenRepositoryAdapter}, including the
 * codified invariant that the secret token value is never projected onto
 * the domain record.
 */
@ExtendWith(MockitoExtension.class)
class TurDevTokenRepositoryAdapterTest {

    @Mock
    private TurDevTokenRepository turDevTokenRepository;

    private TurDevTokenRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TurDevTokenRepositoryAdapter(turDevTokenRepository,
                Mappers.getMapper(TurDevTokenDomainMapper.class));
    }

    @Test
    void findByIdProjectsScalarsButNotTheTokenSecret() {
        TurDevToken entity = new TurDevToken();
        entity.setId("tok-1");
        entity.setTitle("Slack notifier");
        entity.setDescription("Pushes events to Slack");
        entity.setToken("super-secret-bearer-value");
        when(turDevTokenRepository.findById("tok-1")).thenReturn(Optional.of(entity));

        TurDevTokenDomain domain = adapter.findById("tok-1").orElseThrow();

        assertThat(domain.id()).isEqualTo("tok-1");
        assertThat(domain.title()).isEqualTo("Slack notifier");
        assertThat(domain.description()).isEqualTo("Pushes events to Slack");
        assertThat(TurDevTokenDomain.class.getRecordComponents())
                .extracting(rc -> rc.getName())
                .doesNotContain("token");
    }

    @Test
    void findByTokenLooksUpBySecretButReturnsDomainWithoutIt() {
        TurDevToken entity = new TurDevToken();
        entity.setId("tok-1");
        entity.setTitle("Slack notifier");
        entity.setToken("super-secret-bearer-value");
        when(turDevTokenRepository.findByToken("super-secret-bearer-value"))
                .thenReturn(Optional.of(entity));

        TurDevTokenDomain domain = adapter.findByToken("super-secret-bearer-value").orElseThrow();

        // Secret used as input only; domain echoes title/id for audit logs.
        assertThat(domain.id()).isEqualTo("tok-1");
        assertThat(domain.title()).isEqualTo("Slack notifier");
    }

    @Test
    void findByTokenEmpty() {
        when(turDevTokenRepository.findByToken("missing")).thenReturn(Optional.empty());
        assertThat(adapter.findByToken("missing")).isEmpty();
    }
}
