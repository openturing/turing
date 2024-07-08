package com.viglet.turing.api.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

import java.util.List;

@Builder
@Getter
@Setter
public class TurLlmResponse {
    private String id;
    private long created;
    private String object;
    private String model;
    private List<TurLlmChoiceResponse> choices;
    private TurLlmUsageResponse usage;
    @JsonProperty("system_fingerprint")
    private String systemFingerprint;

    @Tolerate
    public TurLlmResponse() {
        super();
    }

}
