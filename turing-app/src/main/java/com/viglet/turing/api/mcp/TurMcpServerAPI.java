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
import com.viglet.turing.persistence.repository.mcp.TurMcpServerRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/mcp")
@Tag(name = "MCP Server", description = "MCP Server API")
public class TurMcpServerAPI {
    private final TurMcpServerRepository turMcpServerRepository;
    private final TurMcpServerMapper turMcpServerMapper;

    public TurMcpServerAPI(TurMcpServerRepository turMcpServerRepository,
            TurMcpServerMapper turMcpServerMapper) {
        this.turMcpServerRepository = turMcpServerRepository;
        this.turMcpServerMapper = turMcpServerMapper;
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
        TurMcpServer turMcpServer = turMcpServerMapper.toEntity(turMcpServerDto);
        return turMcpServerRepository.findById(id).map(turMcpServerEdit -> {
            turMcpServerEdit.setTitle(turMcpServer.getTitle());
            turMcpServerEdit.setDescription(turMcpServer.getDescription());
            turMcpServerEdit.setIcon(turMcpServer.getIcon());
            turMcpServerEdit.setUrl(turMcpServer.getUrl());
            turMcpServerEdit.setCommand(turMcpServer.getCommand());
            turMcpServerEdit.setArgs(turMcpServer.getArgs());
            turMcpServerEdit.setType(turMcpServer.getType());
            turMcpServerEdit.setConnectionType(turMcpServer.getConnectionType());
            turMcpServerEdit.setEnabled(turMcpServer.getEnabled());
            this.turMcpServerRepository.save(turMcpServerEdit);
            return turMcpServerMapper.toDto(turMcpServerEdit);
        }).orElse(new TurMcpServerDto());
    }

    @Transactional
    @Operation(summary = "Delete a MCP Server")
    @DeleteMapping("/{id}")
    public boolean turMcpServerDelete(@PathVariable String id) {
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
