package com.viglet.turing.exchange.sn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteGenAiExchange {
    private String id;
    private boolean enabled;
    private String systemPrompt;
    private String turLLMInstance;
    private String turStoreInstance;
}
