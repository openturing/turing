package com.viglet.turing.api.llm;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;

@Builder
@Getter
@Setter
public class TurLlmQaQuestion {
    private String question;
    private String answer;

    @Tolerate
    public TurLlmQaQuestion() {
        super();
    }
}
