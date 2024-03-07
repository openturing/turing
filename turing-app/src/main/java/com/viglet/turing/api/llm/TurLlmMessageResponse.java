package com.viglet.turing.api.llm;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Builder
@Getter
@Setter
public class TurLlmMessageResponse {
    private String role;
    private String content;

    @Tolerate
    public TurLlmMessageResponse() {
        super();
    }
}
