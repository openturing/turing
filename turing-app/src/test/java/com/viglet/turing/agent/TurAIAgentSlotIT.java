/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.api.agent.TurAIAgentSlotAPI;
import com.viglet.turing.persistence.dto.agent.TurAIAgentSlotDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;
import com.viglet.turing.persistence.model.agent.TurAIAgentSlotType;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.agent.TurAIAgentSlotRepository;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * REST + persistence coverage for the AI Agent typed-slot catalogue:
 * controller CRUD, name validation, the unique {@code (agent_id, name)}
 * constraint, and the {@code ON DELETE CASCADE} from {@code ai_agent} to
 * {@code ai_agent_slot} wired by the {@code v2026.2.7.2} migration.
 *
 * <p>Engine-side coverage (the {@code walkThroughSatisfiedQuestions}
 * walker that consumes slot values) lives in
 * {@code TurChatFlowSkipIfFilledEngineIT} next to the other chat-flow
 * engine ITs, where the graph builders and state helpers already exist.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurAIAgentSlotIT extends AbstractTuringSpringIT {

    @Autowired
    private TurAIAgentRepository agentRepository;
    @Autowired
    private TurAIAgentSlotRepository slotRepository;
    @Autowired
    private TurAIAgentSlotAPI slotApi;

    private TurAIAgent agent;

    @BeforeEach
    void newAgent() {
        // The controller methods are @Secured("ROLE_ADMIN" / "AI_AGENT_*"); calling
        // them through the Spring proxy fires the security interceptor, so we
        // populate the context with an admin principal for the test.
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(new TestingAuthenticationToken("admin", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        SecurityContextHolder.setContext(ctx);

        TurAIAgent a = new TurAIAgent();
        a.setTitle("slot-it-" + UUID.randomUUID().toString().substring(0, 8));
        a.setEnabled(1);
        agent = agentRepository.save(a);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_persistsAndReturnsDto() {
        TurAIAgentSlotDto dto = newSlotDto("visitor_name", TurAIAgentSlotType.STRING,
                "Visitor's preferred name");

        TurAIAgentSlotDto saved = slotApi.create(agent.getId(), dto);

        assertThat(saved.getId()).isNotBlank();
        assertThat(saved.getName()).isEqualTo("visitor_name");
        assertThat(saved.getType()).isEqualTo(TurAIAgentSlotType.STRING);
        assertThat(saved.getDescription()).isEqualTo("Visitor's preferred name");

        // Persisted row carries the agent back-reference even though the
        // DTO hides it via @JsonIgnore.
        TurAIAgentSlot persisted = slotRepository.findById(saved.getId()).orElseThrow();
        assertThat(persisted.getTurAIAgent().getId()).isEqualTo(agent.getId());
    }

    @Test
    void list_returnsAgentSlotsAlphabetically() {
        slotApi.create(agent.getId(),
                newSlotDto("zeta", TurAIAgentSlotType.STRING, null));
        slotApi.create(agent.getId(),
                newSlotDto("alpha", TurAIAgentSlotType.INTEGER, null));
        slotApi.create(agent.getId(),
                newSlotDto("middle", TurAIAgentSlotType.BOOLEAN, null));

        List<TurAIAgentSlotDto> all = slotApi.list(agent.getId());

        assertThat(all)
                .extracting(TurAIAgentSlotDto::getName)
                .containsExactly("alpha", "middle", "zeta");
    }

    @Test
    void list_isAgentScoped() {
        slotApi.create(agent.getId(),
                newSlotDto("mine", TurAIAgentSlotType.STRING, null));
        TurAIAgent other = freshAgent();
        slotApi.create(other.getId(),
                newSlotDto("theirs", TurAIAgentSlotType.STRING, null));

        assertThat(slotApi.list(agent.getId()))
                .extracting(TurAIAgentSlotDto::getName)
                .containsExactly("mine");
        assertThat(slotApi.list(other.getId()))
                .extracting(TurAIAgentSlotDto::getName)
                .containsExactly("theirs");
    }

    @Test
    void update_changesTypeAndDescription() {
        TurAIAgentSlotDto created = slotApi.create(agent.getId(),
                newSlotDto("age", TurAIAgentSlotType.STRING, "as string"));

        TurAIAgentSlotDto patch = newSlotDto("age", TurAIAgentSlotType.INTEGER,
                "promoted to integer");
        TurAIAgentSlotDto updated = slotApi.update(agent.getId(), created.getId(), patch);

        assertThat(updated.getType()).isEqualTo(TurAIAgentSlotType.INTEGER);
        assertThat(updated.getDescription()).isEqualTo("promoted to integer");

        TurAIAgentSlot reloaded = slotRepository.findById(created.getId()).orElseThrow();
        assertThat(reloaded.getType()).isEqualTo(TurAIAgentSlotType.INTEGER);
    }

    @Test
    void delete_removesRow() {
        TurAIAgentSlotDto created = slotApi.create(agent.getId(),
                newSlotDto("temp", TurAIAgentSlotType.TEXT, null));

        boolean removed = slotApi.delete(agent.getId(), created.getId());

        assertThat(removed).isTrue();
        assertThat(slotRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void duplicateName_isRejectedWithConflict() {
        String agentId = agent.getId();
        slotApi.create(agentId, newSlotDto("email", TurAIAgentSlotType.STRING, null));
        TurAIAgentSlotDto duplicate = newSlotDto("email", TurAIAgentSlotType.STRING, null);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> slotApi.create(agentId, duplicate))
                .satisfies(ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void sameNameOnDifferentAgents_isAllowed() {
        slotApi.create(agent.getId(),
                newSlotDto("shared", TurAIAgentSlotType.STRING, null));
        TurAIAgent other = freshAgent();

        // Should NOT raise — the unique constraint is scoped per-agent.
        TurAIAgentSlotDto saved = slotApi.create(other.getId(),
                newSlotDto("shared", TurAIAgentSlotType.STRING, null));
        assertThat(saved.getName()).isEqualTo("shared");
    }

    @Test
    void invalidName_isRejectedWithBadRequest() {
        // Leading digit, space, dot, and empty are all rejected by the
        // controller's validation before any DB round-trip.
        assertInvalidName("1starts_with_digit");
        assertInvalidName("has space");
        assertInvalidName("with.dot");
        assertInvalidName("");
    }

    @Test
    void renamingToTakenName_isRejected() {
        String agentId = agent.getId();
        slotApi.create(agentId, newSlotDto("first", TurAIAgentSlotType.STRING, null));
        TurAIAgentSlotDto second = slotApi.create(agentId,
                newSlotDto("second", TurAIAgentSlotType.STRING, null));
        String secondId = second.getId();
        TurAIAgentSlotDto rename = newSlotDto("first", TurAIAgentSlotType.STRING, null);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> slotApi.update(agentId, secondId, rename))
                .satisfies(ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void renamingToSameName_isAllowed() {
        // Edit-without-rename must not trip the duplicate check.
        TurAIAgentSlotDto created = slotApi.create(agent.getId(),
                newSlotDto("keep_me", TurAIAgentSlotType.STRING, "v1"));

        TurAIAgentSlotDto patch = newSlotDto("keep_me", TurAIAgentSlotType.STRING, "v2");
        TurAIAgentSlotDto updated = slotApi.update(agent.getId(), created.getId(), patch);

        assertThat(updated.getDescription()).isEqualTo("v2");
    }

    @Test
    void agentScopeCrossing_isHidden() {
        // A slot belonging to one agent must not be visible (or editable)
        // through another agent's path — the controller's filter blocks it.
        TurAIAgentSlotDto created = slotApi.create(agent.getId(),
                newSlotDto("mine", TurAIAgentSlotType.STRING, null));
        String otherId = freshAgent().getId();
        String slotId = created.getId();

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> slotApi.get(otherId, slotId))
                .satisfies(ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));

        assertThat(slotApi.delete(otherId, slotId)).isFalse();
    }

    @Test
    void cascadeOnAgentDelete_dropsOrphans() {
        // The v2026.2.7.2 migration declares ON DELETE CASCADE on the FK
        // back to ai_agent. Removing the parent must wipe its slots.
        TurAIAgent victim = freshAgent();
        String victimId = victim.getId();
        slotApi.create(victimId, newSlotDto("doomed", TurAIAgentSlotType.STRING, null));
        assertThat(slotRepository.findByTurAIAgent_IdOrderByNameAsc(victimId))
                .extracting(TurAIAgentSlot::getName)
                .containsExactly("doomed");

        agentRepository.delete(victim);

        // Query via the non-@Cacheable path so a stale findById cache entry
        // doesn't mask the cascade — what we actually care about is the row
        // no longer being attached to the agent.
        assertThat(slotRepository.findByTurAIAgent_IdOrderByNameAsc(victimId)).isEmpty();
    }

    // ─────────────────────────── Helpers ───────────────────────────

    private TurAIAgent freshAgent() {
        TurAIAgent a = new TurAIAgent();
        a.setTitle("slot-it-other-" + UUID.randomUUID().toString().substring(0, 6));
        a.setEnabled(1);
        return agentRepository.save(a);
    }

    private void assertInvalidName(String name) {
        String agentId = agent.getId();
        TurAIAgentSlotDto bad = newSlotDto(name, TurAIAgentSlotType.STRING, null);
        assertThatExceptionOfType(ResponseStatusException.class)
                .as("name '%s' should be rejected", name)
                .isThrownBy(() -> slotApi.create(agentId, bad))
                .satisfies(ex -> assertThat(ex.getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static TurAIAgentSlotDto newSlotDto(String name, TurAIAgentSlotType type,
            String description) {
        TurAIAgentSlotDto dto = new TurAIAgentSlotDto();
        dto.setName(name);
        dto.setType(type);
        dto.setDescription(description);
        return dto;
    }
}
