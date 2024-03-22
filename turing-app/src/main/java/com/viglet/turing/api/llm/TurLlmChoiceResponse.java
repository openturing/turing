package com.viglet.turing.api.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Builder
@Getter
@Setter
public class TurLlmChoiceResponse {
    private int index;
    private TurLlmMessageResponse message;
    private String logprobs;
    @JsonProperty("finish_reason")
    private String finishReason;

    @Tolerate
    public TurLlmChoiceResponse() {
        super();
    }
}
