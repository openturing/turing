package com.viglet.turing.connector.webcrawler.api;

import com.google.inject.Inject;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCAllowUrl;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCAttributeMapping;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCFileExtension;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCSource;
import com.viglet.turing.connector.webcrawler.persistence.repository.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v2/wc/source")
@Tag(name = "Web Crawler Source", description = "Web Crawler Source API")
public class TurWCSourceApi {
    private final TurWCSourceRepository turWCSourceRepository;
    private final TurWCAllowUrlRepository turWCAllowUrlRepository;
    private final TurWCNotAllowUrlRepository turWCNotAllowUrlRepository;
    private final TurWCFileExtensionRepository turWCFileExtensionRepository;
    private final TurWCAttributeMappingRepository turWCAttributeMappingRepository;
    @Inject
    public TurWCSourceApi(TurWCSourceRepository turWCSourceRepository,
                          TurWCAllowUrlRepository turWCAllowUrlRepository,
                          TurWCNotAllowUrlRepository turWCNotAllowUrlRepository,
                          TurWCFileExtensionRepository turWCFileExtensionRepository,
                          TurWCAttributeMappingRepository turWCAttributeMappingRepository) {
        this.turWCSourceRepository = turWCSourceRepository;
        this.turWCAllowUrlRepository = turWCAllowUrlRepository;
        this.turWCNotAllowUrlRepository = turWCNotAllowUrlRepository;
        this.turWCFileExtensionRepository = turWCFileExtensionRepository;
        this.turWCAttributeMappingRepository = turWCAttributeMappingRepository;
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
        return this.turWCSourceRepository.findById(id).map(turWcSource -> {
            turWCAllowUrlRepository.findByTurWCSource(turWcSource)
                    .ifPresent(turWcSource::setAllowUrls);
            turWCNotAllowUrlRepository.findByTurWCSource(turWcSource)
                    .ifPresent(turWcSource::setNotAllowUrls);
            turWCFileExtensionRepository.findByTurWCSource(turWcSource)
                    .ifPresent(turWcSource::setNotAllowExtensions);
            turWCAttributeMappingRepository.findByTurWCSource(turWcSource)
                    .ifPresent(turWcSource::setAttributeMappings);
            return turWcSource;
        }).orElse(new TurWCSource());
    }

    @Operation(summary = "Update a Web Crawler Source")
    @PutMapping("/{id}")
    public TurWCSource turWCSourceUpdate(@PathVariable String id, @RequestBody TurWCSource turWCSource) {
        return turWCSourceRepository.findById(id).map(turWCSourceEdit -> {
            turWCSourceEdit.setTitle(turWCSource.getTitle());
            turWCSourceEdit.setDescription(turWCSource.getDescription());
            turWCSourceEdit.setLocale(turWCSource.getLocale());
            turWCSourceEdit.setTurSNSite(turWCSource.getTurSNSite());
            turWCSourceEdit.setUrl(turWCSource.getUrl());
            turWCSourceEdit.setAllowUrls(turWCSource.getAllowUrls()
                    .stream()
                    .peek(allowUrl ->
                            allowUrl.setTurWCSource(turWCSource))
                    .collect(Collectors.toSet()));
            turWCSourceEdit.setNotAllowUrls(turWCSource.getNotAllowUrls()
                    .stream()
                    .peek(notAllowUrl ->
                            notAllowUrl.setTurWCSource(turWCSource))
                    .collect(Collectors.toSet()));
            turWCSourceEdit.setNotAllowExtensions(turWCSource.getNotAllowExtensions()
                    .stream()
                    .peek(fileExtension ->
                            fileExtension.setTurWCSource(turWCSource))
                    .collect(Collectors.toSet()));
            turWCSourceEdit.setAttributeMappings(turWCSource.getAttributeMappings()
                    .stream()
                    .peek(attributeMapping ->
                            attributeMapping.setTurWCSource(turWCSource))
                    .collect(Collectors.toSet()));
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
