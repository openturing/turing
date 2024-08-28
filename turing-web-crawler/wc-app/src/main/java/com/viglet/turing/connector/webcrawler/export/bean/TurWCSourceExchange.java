package com.viglet.turing.connector.webcrawler.export.bean;

import lombok.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TurWCSourceExchange {
    private String id;
    private Locale locale;
    private String localeClass;
    private String url;
    @Builder.Default
    private Collection<String> turSNSites = new HashSet<>();;
    private String username;
    private String password;
    @Builder.Default
    private Collection<String> allowUrls = new HashSet<>();
    @Builder.Default
    private Collection<String> notAllowUrls = new HashSet<>();
    @Builder.Default
    private Collection<String> notAllowExtensions = new HashSet<>();
    @Builder.Default
    private Collection<TurWCAttribExchange> attributes = new HashSet<>();
}
