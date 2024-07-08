package com.viglet.turing.connector.aem.commons.context;

import lombok.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;


@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class TurAemSourceContext {
    private String url;
    private String username;
    private String password;
    private String rootPath;
    private String contentType;
    private String subType;
    private String turSNSite;
    private String siteName;
    private Locale defaultLocale;
    private String providerName;
    private String group;
    private String urlPrefix;
    private String oncePattern;
    @Builder.Default
    private Collection<TurAemLocalePathContext> localePaths = new HashSet<>();
}
