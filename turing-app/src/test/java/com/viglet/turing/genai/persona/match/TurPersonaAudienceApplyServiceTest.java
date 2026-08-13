/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.genai.persona.match.dto.TurPersonaAudienceApplyDto.ConfirmedField;
import com.viglet.turing.genai.persona.match.dto.TurPersonaAudienceApplyDto.Request;
import com.viglet.turing.genai.persona.match.dto.TurPersonaAudienceApplyDto.Response;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaAudienceApplyLog;
import com.viglet.turing.persistence.model.persona.TurPersonaAudienceField;
import com.viglet.turing.persistence.model.persona.TurPersonaDomainExpertise;
import com.viglet.turing.persistence.model.persona.TurPersonaKind;
import com.viglet.turing.persistence.model.persona.TurPersonaReadingLevel;
import com.viglet.turing.persistence.model.persona.match.TurPersonaMatchPersona;
import com.viglet.turing.persistence.repository.persona.TurPersonaAudienceApplyLogRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchPersonaRepository;

/**
 * T831 / §LXI.7 (Block BN) — the apply half of the tuning loop writes exactly the
 * fields the operator confirmed, nothing else, and every write leaves a row saying
 * which suggestion caused it.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@ExtendWith(MockitoExtension.class)
class TurPersonaAudienceApplyServiceTest {

    private static final String PROJECT = "p1";
    private static final String PERSONA = "a1";
    private static final String OPERATOR = "admin";
    private static final String RATIONALE = "Contents average 41% fit for this persona.";

    @Mock
    private TurPersonaRepository personaRepository;

    @Mock
    private TurPersonaMatchPersonaRepository personaJoinRepository;

    @Mock
    private TurPersonaAudienceApplyLogRepository applyLogRepository;

    @InjectMocks
    private TurPersonaAudienceApplyService service;

    private TurPersona persona;

    @BeforeEach
    void seedPersona() {
        TurPersonaAudience audience = new TurPersonaAudience();
        audience.setReadingLevel(TurPersonaReadingLevel.GRADUATE);
        audience.setDomainExpertise(TurPersonaDomainExpertise.EXPERT);
        audience.setVocabularyCeiling("unrestricted");
        audience.setComprehensionNotes("reads closely");
        // Deliberately populated: the two fields no suggestion may reach.
        audience.setAccessibilityNotes("high contrast");
        audience.setPrimaryLanguage("pt");

        persona = new TurPersona();
        persona.setId(PERSONA);
        persona.setName("Researcher");
        persona.setPersonaKind(TurPersonaKind.AUDIENCE);
        persona.setAudience(audience);
    }

    private void personaIsInProject() {
        TurPersonaMatchPersona join = new TurPersonaMatchPersona();
        join.setPersonaId(PERSONA);
        when(personaJoinRepository.findByProject_Id(PROJECT)).thenReturn(List.of(join));
        when(personaRepository.findById(PERSONA)).thenReturn(Optional.of(persona));
    }

    private static Request request(ConfirmedField... fields) {
        return new Request(PERSONA, RATIONALE, true, List.of(fields));
    }

    // ---- the write is exactly what was confirmed ---------------------------

    @Test
    void writesOnlyTheConfirmedFieldAndLeavesEveryOtherAudienceFieldAlone() {
        personaIsInProject();

        Response response = service.apply(PROJECT,
                request(new ConfirmedField("READING_LEVEL", "SECONDARY")), OPERATOR);

        assertThat(response.applied()).singleElement().satisfies(f -> {
            assertThat(f.field()).isEqualTo("READING_LEVEL");
            assertThat(f.oldValue()).isEqualTo("GRADUATE");
            assertThat(f.newValue()).isEqualTo("SECONDARY");
        });
        assertThat(response.skipped()).isEmpty();

        TurPersonaAudience after = persona.getAudience();
        assertThat(after.getReadingLevel()).isEqualTo(TurPersonaReadingLevel.SECONDARY);
        // The whole point of field-by-field confirmation: nothing else moved.
        assertThat(after.getDomainExpertise()).isEqualTo(TurPersonaDomainExpertise.EXPERT);
        assertThat(after.getVocabularyCeiling()).isEqualTo("unrestricted");
        assertThat(after.getComprehensionNotes()).isEqualTo("reads closely");
        assertThat(after.getAccessibilityNotes()).isEqualTo("high contrast");
        assertThat(after.getPrimaryLanguage()).isEqualTo("pt");
        verify(personaRepository).save(persona);
    }

    @Test
    void writesEveryConfirmedFieldWhenMoreThanOneIsTicked() {
        personaIsInProject();

        Response response = service.apply(PROJECT,
                request(new ConfirmedField("READING_LEVEL", "MIDDLE"),
                        new ConfirmedField("COMPREHENSION_NOTES", "skims rather than reads")),
                OPERATOR);

        assertThat(response.applied()).hasSize(2);
        assertThat(persona.getAudience().getReadingLevel())
                .isEqualTo(TurPersonaReadingLevel.MIDDLE);
        assertThat(persona.getAudience().getComprehensionNotes())
                .isEqualTo("skims rather than reads");
        assertThat(persona.getAudience().getDomainExpertise())
                .isEqualTo(TurPersonaDomainExpertise.EXPERT);
    }

    @Test
    void createsTheAudienceFacetWhenThePersonaHadNone() {
        persona.setAudience(null);
        personaIsInProject();

        Response response = service.apply(PROJECT,
                request(new ConfirmedField("READING_LEVEL", "ELEMENTARY")), OPERATOR);

        assertThat(response.applied()).singleElement()
                .satisfies(f -> assertThat(f.oldValue()).isNull());
        assertThat(persona.getAudience().getReadingLevel())
                .isEqualTo(TurPersonaReadingLevel.ELEMENTARY);
    }

    // ---- provenance --------------------------------------------------------

    @Test
    void stampsOneRowPerAppliedFieldWithTheValueItReplaced() {
        personaIsInProject();

        service.apply(PROJECT, request(new ConfirmedField("READING_LEVEL", "SECONDARY"),
                new ConfirmedField("DOMAIN_EXPERTISE", "INTERMEDIATE")), OPERATOR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TurPersonaAudienceApplyLog>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(applyLogRepository).saveAll(captor.capture());

        List<TurPersonaAudienceApplyLog> stamps = captor.getValue();
        assertThat(stamps).hasSize(2);
        assertThat(stamps).allSatisfy(entry -> {
            assertThat(entry.getPersonaId()).isEqualTo(PERSONA);
            assertThat(entry.getProjectId()).isEqualTo(PROJECT);
            assertThat(entry.getRationale()).isEqualTo(RATIONALE);
            assertThat(entry.getAppliedBy()).isEqualTo(OPERATOR);
            assertThat(entry.isLlmUsed()).isTrue();
            assertThat(entry.getAppliedAt()).isNotNull();
        });
        assertThat(stamps).anySatisfy(entry -> {
            assertThat(entry.getField()).isEqualTo(TurPersonaAudienceField.READING_LEVEL);
            assertThat(entry.getOldValue()).isEqualTo("GRADUATE");
            assertThat(entry.getNewValue()).isEqualTo("SECONDARY");
        });
        assertThat(stamps).anySatisfy(entry -> {
            assertThat(entry.getField()).isEqualTo(TurPersonaAudienceField.DOMAIN_EXPERTISE);
            assertThat(entry.getOldValue()).isEqualTo("EXPERT");
            assertThat(entry.getNewValue()).isEqualTo("INTERMEDIATE");
        });
    }

    @Test
    void recordsThatTheDeterministicFallbackProposedIt() {
        personaIsInProject();

        service.apply(PROJECT, new Request(PERSONA, RATIONALE, false,
                List.of(new ConfirmedField("READING_LEVEL", "MIDDLE"))), OPERATOR);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TurPersonaAudienceApplyLog>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(applyLogRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(entry -> assertThat(entry.isLlmUsed()).isFalse());
    }

    // ---- the gates ---------------------------------------------------------

    @Test
    void skipsAFieldThatIsNotWritableInsteadOfDiscardingTheOtherConfirmations() {
        personaIsInProject();

        Response response = service.apply(PROJECT,
                request(new ConfirmedField("accessibilityNotes", "dyslexia-friendly"),
                        new ConfirmedField("READING_LEVEL", "MIDDLE")),
                OPERATOR);

        assertThat(response.applied()).singleElement()
                .satisfies(f -> assertThat(f.field()).isEqualTo("READING_LEVEL"));
        assertThat(response.skipped()).singleElement().satisfies(f -> {
            assertThat(f.field()).isEqualTo("accessibilityNotes");
            assertThat(f.reason()).contains("not a writable audience field");
        });
        assertThat(persona.getAudience().getAccessibilityNotes()).isEqualTo("high contrast");
    }

    @Test
    void skipsAValueAnEnumeratedFieldDoesNotAccept() {
        personaIsInProject();

        Response response = service.apply(PROJECT,
                request(new ConfirmedField("READING_LEVEL", "POSTGRADUATE")), OPERATOR);

        assertThat(response.applied()).isEmpty();
        assertThat(response.skipped()).singleElement()
                .satisfies(f -> assertThat(f.reason()).contains("UNDERGRADUATE"));
        assertThat(persona.getAudience().getReadingLevel())
                .isEqualTo(TurPersonaReadingLevel.GRADUATE);
    }

    @Test
    void skipsAValueAlreadyInPlaceAndWritesNothingAtAll() {
        personaIsInProject();

        Response response = service.apply(PROJECT,
                request(new ConfirmedField("READING_LEVEL", "GRADUATE")), OPERATOR);

        assertThat(response.applied()).isEmpty();
        assertThat(response.skipped()).singleElement()
                .satisfies(f -> assertThat(f.reason()).contains("already set"));
        // Nothing changed, so neither the persona nor a provenance row is saved.
        verify(personaRepository, never()).save(any());
        verify(applyLogRepository, never()).saveAll(any());
    }

    @Test
    void refusesAPersonaTheProjectDoesNotScore() {
        when(personaJoinRepository.findByProject_Id(PROJECT)).thenReturn(List.of());

        assertThatThrownBy(() -> service.apply(PROJECT,
                request(new ConfirmedField("READING_LEVEL", "MIDDLE")), OPERATOR))
                .isInstanceOf(TurNotFoundException.class);
        verify(personaRepository, never()).save(any());
    }

    @Test
    void refusesAPersonaThatIsNotUsableAsAnAudience() {
        persona.setPersonaKind(TurPersonaKind.SPEAKER);
        personaIsInProject();

        assertThatThrownBy(() -> service.apply(PROJECT,
                request(new ConfirmedField("READING_LEVEL", "MIDDLE")), OPERATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not usable as an audience");
        verify(personaRepository, never()).save(any());
    }

    @Test
    void refusesAnApplyWithNothingConfirmed() {
        assertThatThrownBy(() -> service.apply(PROJECT,
                new Request(PERSONA, RATIONALE, true, List.of()), OPERATOR))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one confirmed field");
        verify(personaJoinRepository, never()).findByProject_Id(any());
    }
}
