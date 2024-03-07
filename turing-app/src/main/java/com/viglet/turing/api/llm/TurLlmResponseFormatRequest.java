package com.viglet.turing.api.llm;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
@Builder
@Getter
@Setter
public class TurLlmResponseFormatRequest {
    private String type;
}
