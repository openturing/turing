package com.viglet.turing.connector.aem.indexer.api;

import com.google.inject.Inject;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSource;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemSourceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v2/aem/source")
@Tag(name = "AEM Source", description = "AEM Source")
public class TurAemSourceApi {

    private final TurAemSourceRepository turAemSourceRepository;
    
    @Inject
    public TurAemSourceApi(TurAemSourceRepository turAemSourceRepository) {
        this.turAemSourceRepository = turAemSourceRepository;
    }

    @GetMapping
    public List<TurAemSource> turAemSourceList() {
        return turAemSourceRepository.findAll();
    }

    @Operation(summary = "AEM Source structure")
    @GetMapping("/structure")
    public TurAemSource turAemSourceStructure() {
        return new TurAemSource();

    }

    @Operation(summary = "Show a AEM Source")
    @GetMapping("/{id}")
    public TurAemSource turAemSourceGet(@PathVariable String id) {
        return this.turAemSourceRepository.findById(id).orElse(new TurAemSource());
    }


    @Operation(summary = "Update a AEM Source")
    @PutMapping("/{id}")
    public TurAemSource turAemSourceUpdate(@PathVariable String id, @RequestBody TurAemSource turAemSource) {
        return turAemSourceRepository.findById(id).map(turAemSourceEdit -> {
            turAemSourceEdit.setGroup(turAemSource.getGroup());
            turAemSourceEdit.setUrl(turAemSource.getUrl());
            turAemSourceEdit.setUsername(turAemSource.getUsername());
            turAemSourceEdit.setPassword(turAemSource.getPassword());
            turAemSourceEdit.setTurSNSite(turAemSource.getTurSNSite());
            turAemSourceEdit.setSiteName(turAemSource.getSiteName());
            turAemSourceEdit.setUrlPrefix(turAemSource.getUrlPrefix());
            turAemSourceEdit.setContentType(turAemSource.getContentType());
            turAemSourceEdit.setSubType(turAemSource.getSubType());
            turAemSourceEdit.setDefaultLocale(turAemSource.getDefaultLocale());
            turAemSourceEdit.setMappingJson(turAemSource.getMappingJson());
            turAemSourceEdit.setProviderName(turAemSource.getProviderName());
            turAemSourceEdit.setOncePattern(turAemSource.getOncePattern());
            turAemSourceEdit.setRootPath(turAemSource.getRootPath());
            this.turAemSourceRepository.save(turAemSourceEdit);
            return turAemSourceEdit;
        }).orElse(new TurAemSource());

    }

    @Transactional
    @Operation(summary = "Delete a AEM Source")
    @DeleteMapping("/{id}")
    public boolean turAemSourceDelete(@PathVariable String id) {
        return turAemSourceRepository.findById(id).map(turAemSource -> {
            turAemSourceRepository.delete(turAemSource);
            return true;
        }).orElse(false);
    }

    @Operation(summary = "Create a AEM Source")
    @PostMapping
    public TurAemSource turAemSourceAdd(@RequestBody TurAemSource turAemSource) {
        this.turAemSourceRepository.save(turAemSource);
        return turAemSource;

    }
}