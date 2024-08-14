package com.viglet.turing.connector.webcrawler.api;

import com.google.inject.Inject;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.connector.webcrawler.TurWCProcess;
import com.viglet.turing.connector.webcrawler.bean.TurWCIndexing;
import com.viglet.turing.connector.webcrawler.export.TurWCExchangeProcess;
import com.viglet.turing.connector.webcrawler.persistence.repository.TurWCSourceRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Log4j2
@RestController
@RequestMapping("/api/v2/wc")
@Tag(name = "Heartbeat", description = "Heartbeat")
public class TurWCApi {
    private final TurWCExchangeProcess turWCExchangeProcess;
    private final TurWCProcess turWCProcess;
    private final TurWCSourceRepository turWCSourceRepository;

    @Inject
    public TurWCApi(TurWCExchangeProcess turWCExchangeProcess,
                    TurWCProcess turWCProcess,
                    TurWCSourceRepository turWCSourceRepository) {
        this.turWCExchangeProcess = turWCExchangeProcess;
        this.turWCProcess = turWCProcess;
        this.turWCSourceRepository = turWCSourceRepository;
    }

    @GetMapping
    public Map<String, String> info() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "ok");
        return status;
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public StreamingResponseBody turSNSiteExport(HttpServletResponse response) {

        try {
            return turWCExchangeProcess.exportObject(response);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    @PostMapping("/import")
    @Transactional
    public void turImport(@RequestParam("file") MultipartFile multipartFile) {
        turWCExchangeProcess.importFromMultipartFile(multipartFile);
    }

    @PostMapping("/indexing")
    public List<TurSNJobItem> turWCSourceUpdate(@RequestBody TurWCIndexing turWCIndexing) {
        return turWCSourceRepository.findAll().stream()
                .filter(turWCSource -> turWCIndexing.getUrl().startsWith(turWCSource.getUrl()))
                .map(turWCSource -> turWCProcess.getPage(turWCSource, turWCIndexing.getUrl()))
                .toList();
    }
}
