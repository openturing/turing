package com.viglet.turing.api.intent;

import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.viglet.turing.persistence.dto.intent.TurIntentDto;
import com.viglet.turing.persistence.mapper.intent.TurIntentMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.intent.TurIntent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.intent.TurIntentRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Agent-scoped REST API for managing Intents and their Actions.
 * Each AI agent owns its own set of intents — there is no longer a global
 * intent listing.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/intent")
@Tag(name = "Intent", description = "AI Agent Intent API")
public class TurIntentAPI {
    private final TurIntentRepository turIntentRepository;
    private final TurIntentMapper turIntentMapper;
    private final TurAIAgentRepository turAIAgentRepository;

    public TurIntentAPI(TurIntentRepository turIntentRepository,
                        TurIntentMapper turIntentMapper,
                        TurAIAgentRepository turAIAgentRepository) {
        this.turIntentRepository = turIntentRepository;
        this.turIntentMapper = turIntentMapper;
        this.turAIAgentRepository = turAIAgentRepository;
    }

    private TurAIAgent loadAgent(String agentId) {
        return turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Agent not found"));
    }

    @Operation(summary = "Intent List for an Agent")
    @GetMapping
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public List<TurIntentDto> turIntentList(@PathVariable String agentId) {
        return turIntentMapper
                .toDtoList(turIntentRepository.findByTurAIAgent_IdOrderByTitleAsc(agentId));
    }

    @Operation(summary = "Intent List for an Agent (enabled only, ordered by sortOrder)")
    @GetMapping("/enabled")
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public List<TurIntentDto> turIntentListEnabled(@PathVariable String agentId) {
        return turIntentMapper
                .toDtoList(turIntentRepository.findByTurAIAgent_IdAndEnabledOrderBySortOrderAsc(agentId, 1));
    }

    @Operation(summary = "Intent structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public TurIntentDto turIntentStructure(@PathVariable String agentId) {
        return turIntentMapper.toDto(new TurIntent());
    }

    @Operation(summary = "Show an Intent of an Agent")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public TurIntentDto turIntentGet(@PathVariable String agentId, @PathVariable String id) {
        return turIntentRepository.findById(id)
                .filter(intent -> intent.getTurAIAgent() != null
                        && agentId.equals(intent.getTurAIAgent().getId()))
                .map(turIntentMapper::toDto)
                .orElse(new TurIntentDto());
    }

    @Operation(summary = "Update an Intent of an Agent")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "INTENT_EDIT"})
    public TurIntentDto turIntentUpdate(@PathVariable String agentId,
                                        @PathVariable String id,
                                        @RequestBody TurIntentDto turIntentDto) {
        TurIntent source = turIntentMapper.toEntity(turIntentDto);
        return turIntentRepository.findById(id)
                .filter(intent -> intent.getTurAIAgent() != null
                        && agentId.equals(intent.getTurAIAgent().getId()))
                .map(existing -> {
                    turIntentMapper.updateEntity(source, existing);
                    turIntentRepository.save(existing);
                    return turIntentMapper.toDto(existing);
                }).orElse(new TurIntentDto());
    }

    @Transactional
    @Operation(summary = "Delete an Intent of an Agent")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "INTENT_DELETE"})
    public boolean turIntentDelete(@PathVariable String agentId, @PathVariable String id) {
        return turIntentRepository.findById(id)
                .filter(intent -> intent.getTurAIAgent() != null
                        && agentId.equals(intent.getTurAIAgent().getId()))
                .map(intent -> {
                    turIntentRepository.delete(id);
                    return true;
                }).orElse(false);
    }

    @Operation(summary = "Create an Intent for an Agent")
    @PostMapping
    @Secured({"ROLE_ADMIN", "INTENT_CREATE"})
    public TurIntentDto turIntentAdd(@PathVariable String agentId,
                                     @RequestBody TurIntentDto turIntentDto) {
        TurAIAgent agent = loadAgent(agentId);
        TurIntent turIntent = turIntentMapper.toEntity(turIntentDto);
        turIntent.setTurAIAgent(agent);
        turIntent.setActions(turIntentDto.getActions());
        turIntentRepository.save(turIntent);
        return turIntentMapper.toDto(turIntent);
    }
}
