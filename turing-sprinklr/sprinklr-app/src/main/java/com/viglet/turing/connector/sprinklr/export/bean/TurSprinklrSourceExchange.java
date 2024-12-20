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
@ToString
public class TurSprinklrSourceExchange {
    private String id;
    private Locale locale;
    private String localeClass;
    private String url;
    private String environment;
    private String secretKey;
    private String apiKey;
    private String tagMapping;
    @Builder.Default
    private Collection<String> turSNSites = new HashSet<>();
    @Builder.Default
    private Collection<TurSprinklrAttribExchange> attributes = new HashSet<>();


}
