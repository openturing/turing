package com.viglet.turing.api.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Builder
@Getter
@Setter
public class TurLlmRequest {
    private String model;
    @JsonProperty("response_format")
    private TurLlmResponseFormatRequest responseFormat;
    private List<TurLlmRoleRequest> messages;
}
