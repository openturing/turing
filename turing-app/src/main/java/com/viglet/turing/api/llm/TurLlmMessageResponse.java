package com.viglet.turing.api.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Builder
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurLlmMessageResponse {
    private String role;
    private String content;
    @Tolerate
    public TurLlmMessageResponse() {
        super();
    }
}
