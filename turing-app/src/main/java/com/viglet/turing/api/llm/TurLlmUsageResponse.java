package com.viglet.turing.api.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Builder
@Getter
@Setter
public class TurLlmUsageResponse {
    @JsonProperty("prompt_tokens")
    private long promptTokens;
    @JsonProperty("completion_tokens")
    private long completionTokens;
    @JsonProperty("total_tokens")
    private long totalTokens;

    @Tolerate
    public TurLlmUsageResponse() {
        super();
    }
}
