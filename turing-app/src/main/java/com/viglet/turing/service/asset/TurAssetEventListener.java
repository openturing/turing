package com.viglet.turing.service.asset;

import com.viglet.turing.system.TurGlobalSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for asset upload/delete events and triggers automatic
 * embedding indexing/deindexing when RAG is enabled.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Component
public class TurAssetEventListener {

    private static final Logger log = LoggerFactory.getLogger(TurAssetEventListener.class);

    private final TurGlobalSettingsService globalSettingsService;
    private final TurAssetTrainingService trainingService;

    public TurAssetEventListener(TurGlobalSettingsService globalSettingsService,
                                  TurAssetTrainingService trainingService) {
        this.globalSettingsService = globalSettingsService;
        this.trainingService = trainingService;
    }

    @Async
    @EventListener
    public void handleAssetEvent(TurAssetEvent event) {
        if (!globalSettingsService.isRagEnabled()) {
            log.debug("[AssetEvent] RAG disabled, ignoring {} event for: {}", event.type(), event.objectName());
            return;
        }

        if (event.type() == TurAssetEvent.Type.UPLOADED) {
            log.info("[AssetEvent] File uploaded, indexing: {}", event.objectName());
            trainingService.indexSingleFile(event.objectName(), event.contentType(), event.size());
        } else if (event.type() == TurAssetEvent.Type.DELETED) {
            log.info("[AssetEvent] File deleted, deindexing: {}", event.objectName());
            trainingService.deindexSingleFile(event.objectName());
        }
    }
}
