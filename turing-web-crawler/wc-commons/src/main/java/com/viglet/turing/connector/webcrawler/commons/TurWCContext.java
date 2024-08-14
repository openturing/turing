package com.viglet.turing.connector.webcrawler.commons;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jsoup.nodes.Document;

@Builder
@Getter
@Setter
public class TurWCContext {
    private String url;
    private String userAgent;
    private String referrer;
    private int timeout;
    private Document document;
}
