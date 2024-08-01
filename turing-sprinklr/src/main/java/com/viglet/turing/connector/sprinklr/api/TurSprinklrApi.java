package com.viglet.turing.connector.sprinklr.api;

import com.google.inject.Inject;
import com.viglet.turing.connector.sprinklr.bean.TurSprinklrSearch;
import com.viglet.turing.connector.sprinklr.kb.TurSprinklrKB;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Log4j2
@RestController
@RequestMapping("/api/v2/sprinklr")
@Tag(name = "Heartbeat", description = "Heartbeat")
public class TurSprinklrApi {
    private final TurSprinklrKB turSprinklrKB;

    @Inject
    public TurSprinklrApi(TurSprinklrKB turSprinklrKB) {
        this.turSprinklrKB = turSprinklrKB;
    }

    @GetMapping(value = "/search")
    public TurSprinklrSearch turSNSiteExport() throws IOException {
        return turSprinklrKB.run();
    }
}
