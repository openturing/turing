/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.answer.TurAnswerShare;
import com.viglet.turing.persistence.repository.answer.TurAnswerShareRepository;
import com.viglet.turing.service.chatslots.TurChatShareOgService;

/**
 * T844 / §LXII.13 — coverage for {@link TurAnswerShareService}: minting an answer
 * permalink snapshot and rendering its Open-Graph card.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurAnswerShareServiceTest {

    @Mock
    private TurAnswerShareRepository repository;
    @Mock
    private TurChatShareOgService shareOgService;

    @InjectMocks
    private TurAnswerShareService service;

    @Test
    void createPersistsTheSnapshotAndReturnsTheId() {
        when(repository.save(any())).thenAnswer(invocation -> {
            TurAnswerShare saved = invocation.getArgument(0);
            saved.setId("share-1");
            return saved;
        });

        String id = service.create("site1", "cheapest model?", "{\"vendor\":[\"Anthropic\"]}",
                "The cheapest is X.", "[{\"sourceId\":\"a\"}]", "[]",
                Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(id).isEqualTo("share-1");
        ArgumentCaptor<TurAnswerShare> captor = ArgumentCaptor.forClass(TurAnswerShare.class);
        verify(repository).save(captor.capture());
        TurAnswerShare saved = captor.getValue();
        assertThat(saved.getQuery()).isEqualTo("cheapest model?");
        assertThat(saved.getAnswerText()).isEqualTo("The cheapest is X.");
        assertThat(saved.getFiltersJson()).isEqualTo("{\"vendor\":[\"Anthropic\"]}");
        assertThat(saved.getCreationDate()).isNotNull();
        assertThat(saved.getAnsweredAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void renderOgCardUsesTheQueryAsTitleAndAnswerAsDescription() {
        TurAnswerShare share = new TurAnswerShare();
        share.setId("s1");
        share.setQuery("cheapest model?");
        share.setAnswerText("The cheapest is X.");
        when(repository.findById("s1")).thenReturn(Optional.of(share));
        when(shareOgService.renderCard(any())).thenReturn("<html>og</html>");

        Optional<String> og = service.renderOgCard("s1", "https://x/answer/shared/s1");

        assertThat(og).contains("<html>og</html>");
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(shareOgService).renderCard(captor.capture());
        assertThat(captor.getValue())
                .containsEntry("title", "cheapest model?")
                .containsEntry("dest", "https://x/answer/shared/s1");
        assertThat(captor.getValue().get("description")).contains("The cheapest is X.");
    }

    @Test
    void renderOgCardIsEmptyForAnUnknownId() {
        when(repository.findById("nope")).thenReturn(Optional.empty());

        assertThat(service.renderOgCard("nope", null)).isEmpty();
    }
}
