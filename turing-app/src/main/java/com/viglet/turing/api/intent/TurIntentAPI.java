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

import com.viglet.turing.persistence.dto.intent.TurIntentDto;
import com.viglet.turing.persistence.mapper.intent.TurIntentMapper;
import com.viglet.turing.persistence.model.intent.TurIntent;
import com.viglet.turing.persistence.repository.intent.TurIntentRepository;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST API for managing Intents and their Actions.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@RestController
@RequestMapping("/api/intent")
@Tag(name = "Intent", description = "Intent API")
public class TurIntentAPI {
    private final TurIntentRepository turIntentRepository;
    private final TurIntentMapper turIntentMapper;

    public TurIntentAPI(TurIntentRepository turIntentRepository,
                        TurIntentMapper turIntentMapper) {
        this.turIntentRepository = turIntentRepository;
        this.turIntentMapper = turIntentMapper;
    }

    @Operation(summary = "Intent List")
    @GetMapping
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public List<TurIntentDto> turIntentList() {
        return turIntentMapper
                .toDtoList(turIntentRepository.findAll(TurPersistenceUtils.orderByTitleIgnoreCase()));
    }

    @Operation(summary = "Intent List (enabled only, ordered by sortOrder)")
    @GetMapping("/enabled")
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public List<TurIntentDto> turIntentListEnabled() {
        return turIntentMapper
                .toDtoList(turIntentRepository.findByEnabledOrderBySortOrderAsc(1));
    }

    @Operation(summary = "Intent structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public TurIntentDto turIntentStructure() {
        return turIntentMapper.toDto(new TurIntent());
    }

    @Operation(summary = "Show an Intent")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public TurIntentDto turIntentGet(@PathVariable String id) {
        return turIntentMapper
                .toDto(turIntentRepository.findById(id).orElse(new TurIntent()));
    }

    @Operation(summary = "Update an Intent")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "INTENT_EDIT"})
    public TurIntentDto turIntentUpdate(@PathVariable String id,
                                        @RequestBody TurIntentDto turIntentDto) {
        TurIntent source = turIntentMapper.toEntity(turIntentDto);
        return turIntentRepository.findById(id).map(existing -> {
            turIntentMapper.updateEntity(source, existing);
            turIntentRepository.save(existing);
            return turIntentMapper.toDto(existing);
        }).orElse(new TurIntentDto());
    }

    @Transactional
    @Operation(summary = "Delete an Intent")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "INTENT_DELETE"})
    public boolean turIntentDelete(@PathVariable String id) {
        turIntentRepository.delete(id);
        return true;
    }

    @Operation(summary = "Create an Intent")
    @PostMapping
    @Secured({"ROLE_ADMIN", "INTENT_CREATE"})
    public TurIntentDto turIntentAdd(@RequestBody TurIntentDto turIntentDto) {
        TurIntent turIntent = turIntentMapper.toEntity(turIntentDto);
        turIntent.setActions(turIntentDto.getActions());
        turIntentRepository.save(turIntent);
        return turIntentMapper.toDto(turIntent);
    }
}
