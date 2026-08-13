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

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.viglet.turing.api.exception.TurNotFoundException;
import com.viglet.turing.genai.persona.match.dto.TurPersonaAudienceApplyDto.AppliedField;
import com.viglet.turing.genai.persona.match.dto.TurPersonaAudienceApplyDto.ConfirmedField;
import com.viglet.turing.genai.persona.match.dto.TurPersonaAudienceApplyDto.Request;
import com.viglet.turing.genai.persona.match.dto.TurPersonaAudienceApplyDto.Response;
import com.viglet.turing.genai.persona.match.dto.TurPersonaAudienceApplyDto.SkippedField;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.persona.TurPersonaAudience;
import com.viglet.turing.persistence.model.persona.TurPersonaAudienceApplyLog;
import com.viglet.turing.persistence.model.persona.TurPersonaAudienceField;
import com.viglet.turing.persistence.repository.persona.TurPersonaAudienceApplyLogRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.persistence.repository.persona.match.TurPersonaMatchPersonaRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Writes the audience fields an operator confirmed on a Persona Match suggestion,
 * and stamps each one with the suggestion it came from (T831 / §LXI.7).
 *
 * <p>This is the only place in the tuning loop that writes to a persona, and it
 * writes exactly what was confirmed: {@link TurPersonaAudienceField} carries a
 * per-field accessor, so an apply mutates one column at a time instead of
 * replacing the whole {@link TurPersonaAudience} embeddable — the wholesale copy
 * the persona {@code PUT} does would clobber the fields the operator deliberately
 * left alone, which is the whole point of confirming field by field.
 *
 * <p>Four gates stand between a request and a column. The persona must be one the
 * project actually scores (so an Apply cannot reach a persona the operator was
 * never shown), it must be usable as an audience at all, the field must be one of
 * the four writable ones, and an enumerated field's value must be a real constant.
 * A field failing any of the last two is <em>reported as skipped</em> rather than
 * thrown, so one bad field cannot discard the operator's other confirmations.
 *
 * <p>Provenance is not best-effort. Unlike the chat-slot write log, whose failures
 * are swallowed so audit never breaks a conversation, a row here shares the
 * transaction with the write it explains: an applied change nobody can attribute
 * is the state T831 exists to end, so if the stamp cannot be written the change
 * does not happen either.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Service
public class TurPersonaAudienceApplyService {

    private static final String PERSONA = "Persona";

    private final TurPersonaRepository personaRepository;
    private final TurPersonaMatchPersonaRepository personaJoinRepository;
    private final TurPersonaAudienceApplyLogRepository applyLogRepository;

    public TurPersonaAudienceApplyService(TurPersonaRepository personaRepository,
            TurPersonaMatchPersonaRepository personaJoinRepository,
            TurPersonaAudienceApplyLogRepository applyLogRepository) {
        this.personaRepository = personaRepository;
        this.personaJoinRepository = personaJoinRepository;
        this.applyLogRepository = applyLogRepository;
    }

    /**
     * Applies the confirmed fields and returns what landed and what did not.
     *
     * @throws TurNotFoundException when the persona is not in the project
     * @throws IllegalArgumentException when the request names no field, or the
     *         persona is not usable as an audience
     */
    @Transactional
    public Response apply(String projectId, Request request, String appliedBy) {
        if (request == null || StringUtils.isBlank(request.personaId())) {
            throw new IllegalArgumentException("personaId is required");
        }
        if (request.fields() == null || request.fields().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one confirmed field is required — an apply with nothing "
                            + "confirmed is not an apply");
        }
        String personaId = request.personaId().strip();
        requireInProject(projectId, personaId);

        TurPersona persona = personaRepository.findById(personaId)
                .orElseThrow(() -> TurNotFoundException.of(PERSONA, personaId));
        if (!persona.isUsableAsAudience()) {
            throw new IllegalArgumentException(
                    "Persona " + personaId + " is not usable as an audience, so it has no "
                            + "audience facet to adjust");
        }

        TurPersonaAudience audience = persona.getAudience();
        if (audience == null) {
            audience = new TurPersonaAudience();
            persona.setAudience(audience);
        }

        Instant appliedAt = Instant.now();
        List<AppliedField> applied = new ArrayList<>();
        List<SkippedField> skipped = new ArrayList<>();
        List<TurPersonaAudienceApplyLog> stamps = new ArrayList<>();
        Set<TurPersonaAudienceField> seen = new LinkedHashSet<>();

        for (ConfirmedField confirmed : request.fields()) {
            applyOne(confirmed, audience, seen, applied, skipped, stamps,
                    new StampContext(projectId, personaId, request, appliedAt, appliedBy));
        }

        if (applied.isEmpty()) {
            // Nothing was written, so there is nothing to stamp and nothing to save.
            return new Response(personaId, appliedAt, List.of(), List.copyOf(skipped));
        }
        personaRepository.save(persona);
        applyLogRepository.saveAll(stamps);
        log.info("[PersonaMatch] applied {} audience field(s) to persona={} from project={}",
                applied.size(), personaId, projectId);
        return new Response(personaId, appliedAt, List.copyOf(applied), List.copyOf(skipped));
    }

    /** One persona's applied history, most recent first — the "why did this change". */
    public List<TurPersonaAudienceApplyLog> history(String personaId) {
        return applyLogRepository.findByPersonaIdOrderByAppliedAtDesc(personaId);
    }

    private void applyOne(ConfirmedField confirmed, TurPersonaAudience audience,
            Set<TurPersonaAudienceField> seen, List<AppliedField> applied,
            List<SkippedField> skipped, List<TurPersonaAudienceApplyLog> stamps,
            StampContext ctx) {
        String rawField = confirmed == null ? null : confirmed.field();
        Optional<TurPersonaAudienceField> parsed = TurPersonaAudienceField.parse(rawField);
        if (parsed.isEmpty()) {
            skipped.add(new SkippedField(rawField, "not a writable audience field"));
            return;
        }
        TurPersonaAudienceField field = parsed.get();
        if (!seen.add(field)) {
            skipped.add(new SkippedField(field.name(), "confirmed more than once"));
            return;
        }
        String value = StringUtils.trimToNull(confirmed.value());
        if (!field.accepts(value)) {
            skipped.add(new SkippedField(field.name(),
                    "value is not one of " + String.join(", ", field.allowedValues())));
            return;
        }
        String oldValue = field.read(audience);
        if (Objects.equals(oldValue, value)) {
            skipped.add(new SkippedField(field.name(), "already set to this value"));
            return;
        }
        field.write(audience, value);
        applied.add(new AppliedField(field.name(), oldValue, value));
        stamps.add(stamp(ctx, field, oldValue, value));
    }

    private TurPersonaAudienceApplyLog stamp(StampContext ctx, TurPersonaAudienceField field,
            String oldValue, String newValue) {
        TurPersonaAudienceApplyLog entry = new TurPersonaAudienceApplyLog();
        entry.setPersonaId(ctx.personaId());
        entry.setProjectId(ctx.projectId());
        entry.setField(field);
        entry.setOldValue(oldValue);
        entry.setNewValue(newValue);
        entry.setRationale(StringUtils.trimToNull(ctx.request().rationale()));
        entry.setLlmUsed(ctx.request().llmUsed());
        entry.setAppliedAt(ctx.appliedAt());
        entry.setAppliedBy(ctx.appliedBy());
        return entry;
    }

    private void requireInProject(String projectId, String personaId) {
        boolean scored = personaJoinRepository.findByProject_Id(projectId).stream()
                .anyMatch(join -> personaId.equals(join.getPersonaId()));
        if (!scored) {
            throw TurNotFoundException.of(PERSONA + " in this project", personaId);
        }
    }

    /** The per-request facts every stamp shares. */
    private record StampContext(String projectId, String personaId, Request request,
            Instant appliedAt, String appliedBy) {
    }
}
