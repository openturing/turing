package com.viglet.turing.connector.aem.indexer.api;

import com.viglet.turing.connector.aem.indexer.TurAemIndexerTool;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2")
@Tag(name = "Heartbeat", description = "Heartbeat")
public class TurAemApi {
    private final TurAemIndexerTool turAemIndexerTool;

    public TurAemApi(TurAemIndexerTool turAemIndexerTool) {
        this.turAemIndexerTool = turAemIndexerTool;
    }

    @GetMapping
    public Map<String, String> info() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }

    @PostMapping("reindex/{group}")
    public Map<String, String> reindex(@PathVariable String group) {
        turAemIndexerTool.reindex(group);
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }

    @PostMapping("reindex/once/{group}")
    public Map<String, String> reIndexOnce(@PathVariable String group) {
        turAemIndexerTool.reindexOnce(group);
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }

    @PostMapping("reindex/{group}/{guid}")
    public Map<String, String> reindexGuid(@PathVariable String group, @PathVariable String guid) {
        turAemIndexerTool.reindexOnce(group);
        turAemIndexerTool.indexGUIDList(List.of(guid));
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }
}