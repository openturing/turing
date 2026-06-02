/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.exchange.agent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.viglet.turing.persistence.model.agent.TurChatFlow;
import com.viglet.turing.persistence.model.agent.TurChatFlowCaptureMode;
import com.viglet.turing.persistence.model.agent.TurChatFlowGuardrailMethod;
import com.viglet.turing.persistence.model.agent.TurChatFlowTriggerMode;

import lombok.Getter;
import lombok.Setter;

/**
 * Export wrapper for {@link TurChatFlow}: carries every metadata field of the
 * source entity but moves the heavyweight React-Flow graph
 * ({@code definitionJson}) out of the envelope and into a sibling file under
 * {@code chat-flows/} in the ZIP. {@link #definitionFile} holds the relative
 * path the importer reads back.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.8
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TurChatFlowExchange {

    private String id;
    private String name;
    private String description;
    private int enabled;
    private TurChatFlowGuardrailMethod guardrailMethod;
    /** T51 — capture-first inversion mode; null on pre-T51 exports. */
    private TurChatFlowCaptureMode captureMode;
    private String triggerDescription;
    private TurChatFlowTriggerMode triggerMode;
    private String experimentKey;
    private String variantLabel;
    private Integer trafficWeight;
    private Instant experimentStartsAt;
    private Instant experimentEndsAt;
    private Boolean banditEnabled;
    /** T71 — champion-challenger auto-promotion opt-in; null on pre-T71 exports. */
    private Boolean autoPromote;

    /** Relative path inside the ZIP to the chat-flow definition JSON. */
    private String definitionFile;

    public static TurChatFlowExchange fromEntity(TurChatFlow flow, String definitionFile) {
        TurChatFlowExchange e = new TurChatFlowExchange();
        e.id = flow.getId();
        e.name = flow.getName();
        e.description = flow.getDescription();
        e.enabled = flow.getEnabled();
        e.guardrailMethod = flow.getGuardrailMethod();
        e.captureMode = flow.getCaptureMode();
        e.triggerDescription = flow.getTriggerDescription();
        e.triggerMode = flow.getTriggerMode();
        e.experimentKey = flow.getExperimentKey();
        e.variantLabel = flow.getVariantLabel();
        e.trafficWeight = flow.getTrafficWeight();
        e.experimentStartsAt = flow.getExperimentStartsAt();
        e.experimentEndsAt = flow.getExperimentEndsAt();
        e.banditEnabled = flow.getBanditEnabled();
        e.autoPromote = flow.getAutoPromote();
        e.definitionFile = definitionFile;
        return e;
    }
}
