package com.viglet.turing.api.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

import com.viglet.turing.persistence.dto.agent.TurAIAgentDto;
import com.viglet.turing.persistence.mapper.agent.TurAIAgentMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/ai-agent")
@Tag(name = "AI Agent", description = "AI Agent API")
public class TurAIAgentAPI {
    private final TurAIAgentRepository turAIAgentRepository;
    private final TurAIAgentMapper turAIAgentMapper;
    private final TurLLMInstanceRepository turLLMInstanceRepository;
    private final TurMcpServerRepository turMcpServerRepository;

    public TurAIAgentAPI(TurAIAgentRepository turAIAgentRepository,
            TurAIAgentMapper turAIAgentMapper,
            TurLLMInstanceRepository turLLMInstanceRepository,
            TurMcpServerRepository turMcpServerRepository) {
        this.turAIAgentRepository = turAIAgentRepository;
        this.turAIAgentMapper = turAIAgentMapper;
        this.turLLMInstanceRepository = turLLMInstanceRepository;
        this.turMcpServerRepository = turMcpServerRepository;
    }

    @Operation(summary = "AI Agent List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public List<TurAIAgentDto> turAIAgentList() {
        return turAIAgentMapper
                .toDtoList(this.turAIAgentRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Operation(summary = "AI Agent structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public TurAIAgentDto turAIAgentStructure() {
        return turAIAgentMapper.toDto(new TurAIAgent());
    }

    @Operation(summary = "Show an AI Agent")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_VIEW"})
    public TurAIAgentDto turAIAgentGet(@PathVariable String id) {
        return turAIAgentMapper
                .toDto(this.turAIAgentRepository.findById(id).orElse(new TurAIAgent()));
    }

    @Operation(summary = "Update an AI Agent")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_EDIT"})
    public TurAIAgentDto turAIAgentUpdate(@PathVariable String id,
            @RequestBody TurAIAgentDto turAIAgentDto) {
        TurAIAgent turAIAgent = turAIAgentMapper.toEntity(turAIAgentDto);
        return turAIAgentRepository.findById(id).map(agentEdit -> {
            agentEdit.setTitle(turAIAgent.getTitle());
            agentEdit.setDescription(turAIAgent.getDescription());
            agentEdit.setIcon(turAIAgent.getIcon());
            agentEdit.setSystemPrompt(turAIAgent.getSystemPrompt());
            agentEdit.setEnabled(turAIAgent.getEnabled());
            agentEdit.setNativeTools(turAIAgent.getNativeTools());
            agentEdit.setLlmInstances(resolveRelations(
                    turAIAgent.getLlmInstances(), turLLMInstanceRepository));
            agentEdit.setMcpServers(resolveRelations(
                    turAIAgent.getMcpServers(), turMcpServerRepository));
            this.turAIAgentRepository.save(agentEdit);
            return turAIAgentMapper.toDto(agentEdit);
        }).orElse(new TurAIAgentDto());
    }

    @Transactional
    @Operation(summary = "Delete an AI Agent")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "AI_AGENT_DELETE"})
    public boolean turAIAgentDelete(@PathVariable String id) {
        this.turAIAgentRepository.delete(id);
        return true;
    }

    @Operation(summary = "Create an AI Agent")
    @PostMapping
    @Secured({"ROLE_ADMIN", "AI_AGENT_CREATE"})
    public TurAIAgentDto turAIAgentAdd(@RequestBody TurAIAgentDto turAIAgentDto) {
        TurAIAgent turAIAgent = turAIAgentMapper.toEntity(turAIAgentDto);
        turAIAgent.setLlmInstances(resolveRelations(
                turAIAgent.getLlmInstances(), turLLMInstanceRepository));
        turAIAgent.setMcpServers(resolveRelations(
                turAIAgent.getMcpServers(), turMcpServerRepository));
        this.turAIAgentRepository.save(turAIAgent);
        return turAIAgentMapper.toDto(turAIAgent);
    }

    private <T> Set<T> resolveRelations(
            Set<T> incoming,
            org.springframework.data.jpa.repository.JpaRepository<T, String> repo) {
        if (incoming == null || incoming.isEmpty()) {
            return new HashSet<>();
        }
        return incoming.stream()
                .flatMap(entity -> {
                    if (entity instanceof org.springframework.data.domain.Persistable) {
                        var persistable = (org.springframework.data.domain.Persistable<?>) entity;
                        String id = (String) persistable.getId();
                        return repo.findById(id).stream();
                    }
                    return java.util.stream.Stream.of(entity);
                })
                .collect(Collectors.toSet());
    }
}
