package com.viglet.turing.connector.aem.indexer.api;

import com.google.inject.Inject;
import com.viglet.turing.connector.aem.indexer.persistence.repository.TurAemIndexingRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "Heartbeat", description = "Heartbeat")
public class TurAemApi {
    private final TurAemIndexingRepository turAemIndexingRepository;

    @Inject
    public TurAemApi(TurAemIndexingRepository turAemIndexingRepository) {
        this.turAemIndexingRepository = turAemIndexingRepository;
    }

    @GetMapping
    public Map<String, String> info() {
        return statusOk();
    }

    @Transactional
    @GetMapping("reindex/{group}")
    public Map<String, String> reindex(@PathVariable String group) {
        turAemIndexingRepository.deleteByIndexGroupAndOnceFalse(group);
        return statusOk();
    }

    @Transactional
    @GetMapping("reindex/once/{group}")
    public Map<String, String> reIndexOnce(@PathVariable String group) {
        turAemIndexingRepository.deleteByIndexGroupAndOnceTrue(group);
        return statusOk();
    }

    @Transactional
    @GetMapping("reindex/{group}/{guid}")
    public Map<String, String> reindexGuid(@PathVariable String group, @PathVariable String guid) {
        turAemIndexingRepository.deleteByAemIdAndIndexGroup(guid, group);
        return statusOk();
    }

    private static @NotNull Map<String, String> statusOk() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }
}