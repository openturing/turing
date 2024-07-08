package com.viglet.turing.api.llm;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

import java.util.List;

@Builder
@Getter
@Setter
public class TurLlmQa {
    private List<TurLlmQaQuestion> questions;

    @Tolerate
    public TurLlmQa() {
        super();
    }
}
