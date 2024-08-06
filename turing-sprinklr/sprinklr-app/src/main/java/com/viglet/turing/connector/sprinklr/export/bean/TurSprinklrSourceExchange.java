package com.viglet.turing.connector.sprinklr.export.bean;

import lombok.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TurSprinklrSourceExchange {
    private String id;
    private Locale locale;
    private String localeClass;
    private String url;
    private String environment;
    private String authorizationCode;
    private String apiKey;
    @Builder.Default
    private Collection<String> turSNSites = new HashSet<>();;
    @Builder.Default
    private Collection<TurSprinklrAttribExchange> attributes = new HashSet<>();


}
