package com.viglet.turing.api.genai;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
@Builder
@Getter
@Setter
public class TurLlmRoleRequest {
    private String role;
    private String content;
}
