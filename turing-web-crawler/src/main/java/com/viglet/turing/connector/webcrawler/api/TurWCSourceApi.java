package com.viglet.turing.connector.webcrawler.api;

import com.google.inject.Inject;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCSource;
import com.viglet.turing.connector.webcrawler.persistence.repository.TurWCSourceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v2/wc")
@Tag(name = "Web Crawler Source", description = "Web Crawler Source API")
public class TurWCSourceApi {
    private final TurWCSourceRepository turWCSourceRepository;
    @Inject
    public TurWCSourceApi(TurWCSourceRepository turWCSourceRepository) {
        this.turWCSourceRepository = turWCSourceRepository;
    }
    @Operation(summary = "Web Crawler Source List")
    @GetMapping
    public List<TurWCSource> turWCSourceList() {
            return turWCSourceRepository.findAll();
    }

    @Operation(summary = "Web Crawler Source structure")
    @GetMapping("/structure")
    public TurWCSource turWCSourceStructure() {
        return new TurWCSource();
    }

    @Operation(summary = "Show a Web Crawler Source")
    @GetMapping("/{id}")
    public TurWCSource turWCSourceGet(@PathVariable String id) {
        return turWCSourceRepository.findById(id).orElse(new TurWCSource());
    }

    @Operation(summary = "Update a Web Crawler Source")
    @PutMapping("/{id}")
    public TurWCSource turWCSourceUpdate(@PathVariable String id, @RequestBody TurWCSource turWCSource) {
        return turWCSourceRepository.findById(id).map(turWCSourceEdit -> {
            turWCSourceEdit.setLocale(turWCSource.getLocale());
            turWCSourceEdit.setTurSNSite(turWCSource.getTurSNSite());
            turWCSourceEdit.setUrl(turWCSource.getUrl());
            turWCSourceEdit.setNotAllowExtensions(turWCSource.getNotAllowExtensions());
            turWCSourceEdit.setAllowUrls(turWCSource.getAllowUrls());
            turWCSourceEdit.setAttributeMappings(turWCSource.getAttributeMappings());
            turWCSourceEdit.setNotAllowUrls(turWCSource.getNotAllowUrls());
            turWCSourceRepository.save(turWCSourceEdit);
            return turWCSourceEdit;
        }).orElse(new TurWCSource());

    }

    @Transactional
    @Operation(summary = "Delete a Web Crawler Source")
    @DeleteMapping("/{id}")
    public boolean turWCSourceDelete(@PathVariable String id) {
       turWCSourceRepository.findById(id).ifPresent(turWCSourceRepository::delete);
        return true;
    }

    @Operation(summary = "Create a Web Crawler Source")
    @PostMapping
    public TurWCSource turWCSourceAdd(@RequestBody TurWCSource turWCSource) {
        turWCSourceRepository.save(turWCSource);
        return turWCSource;
    }
}