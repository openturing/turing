package com.viglet.turing.api.mcp;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.dto.mcp.TurMcpServerDto;
import com.viglet.turing.persistence.mapper.mcp.TurMcpServerMapper;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.persistence.repository.persona.TurPersonaRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "MCP Server", description = "MCP Server API")
public class TurMcpServerAPI {
    private final TurMcpServerRepository turMcpServerRepository;
    private final TurMcpServerMapper turMcpServerMapper;
    private final TurAIAgentRepository turAIAgentRepository;
    private final TurPersonaRepository turPersonaRepository;

    public TurMcpServerAPI(TurMcpServerRepository turMcpServerRepository,
            TurMcpServerMapper turMcpServerMapper,
            TurAIAgentRepository turAIAgentRepository,
            TurPersonaRepository turPersonaRepository) {
        this.turMcpServerRepository = turMcpServerRepository;
        this.turMcpServerMapper = turMcpServerMapper;
        this.turAIAgentRepository = turAIAgentRepository;
        this.turPersonaRepository = turPersonaRepository;
    }

    @Operation(summary = "MCP Server List")
    @GetMapping
    public List<TurMcpServerDto> turMcpServerList() {
        return turMcpServerMapper
                .toDtoList(this.turMcpServerRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Operation(summary = "MCP Server structure")
    @GetMapping("/structure")
    public TurMcpServerDto turMcpServerStructure() {
        return turMcpServerMapper.toDto(new TurMcpServer());
    }

    @Operation(summary = "Show a MCP Server")
    @GetMapping("/{id}")
    public TurMcpServerDto turMcpServerGet(@PathVariable String id) {
        return turMcpServerMapper
                .toDto(this.turMcpServerRepository.findById(id).orElse(new TurMcpServer()));
    }

    @Operation(summary = "Update a MCP Server")
    @PutMapping("/{id}")
    public TurMcpServerDto turMcpServerUpdate(@PathVariable String id,
            @RequestBody TurMcpServerDto turMcpServerDto) {
        TurMcpServer source = turMcpServerMapper.toEntity(turMcpServerDto);
        return turMcpServerRepository.findById(id).map(existing -> {
            turMcpServerMapper.updateEntity(source, existing);
            turMcpServerRepository.save(existing);
            return turMcpServerMapper.toDto(existing);
        }).orElse(new TurMcpServerDto());
    }

    @Transactional
    @Operation(summary = "Delete a MCP Server")
    @DeleteMapping("/{id}")
    public boolean turMcpServerDelete(@PathVariable String id) {
        // Detach every reference to this MCP server before deleting so the
        // FK constraints don't reject the delete:
        //   • ai_agent_mcp_server (M2M join table — remove from agent's catalog)
        //   • ai_persona.brand_context_mcp_server_id (M2O on persona)
        //
        // saveAndFlush instead of save because the next line is a `@Modifying`
        // JPQL bulk delete that bypasses the persistence context — pending
        // updates must hit the DB before the constraint check fires.
        for (var agent : this.turAIAgentRepository.findByMcpServerId(id)) {
            agent.getMcpServers().removeIf(m -> id.equals(m.getId()));
            this.turAIAgentRepository.save(agent);
        }
        this.turAIAgentRepository.flush();
        for (var persona : this.turPersonaRepository.findByBrandContextMcpServer_Id(id)) {
            persona.setBrandContextMcpServer(null);
            this.turPersonaRepository.saveAndFlush(persona);
        }
        this.turMcpServerRepository.delete(id);
        return true;
    }

    @Operation(summary = "Create a MCP Server")
    @PostMapping
    public TurMcpServerDto turMcpServerAdd(@RequestBody TurMcpServerDto turMcpServerDto) {
        TurMcpServer turMcpServer = turMcpServerMapper.toEntity(turMcpServerDto);
        this.turMcpServerRepository.save(turMcpServer);
        return turMcpServerMapper.toDto(turMcpServer);
    }
}
