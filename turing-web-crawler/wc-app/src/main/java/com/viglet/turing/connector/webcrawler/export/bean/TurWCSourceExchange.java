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
    private String turSNSite;
    private String username;
    private String password;
    private Collection<String> allowUrls = new HashSet<>();
    private Collection<String> notAllowUrls = new HashSet<>();
    private Collection<String> notAllowExtensions = new HashSet<>();
    private Collection<TurWCAttribExchange> attributes = new HashSet<>();


}
