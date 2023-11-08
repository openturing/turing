package com.viglet.turing.connector.aem.indexer.bean;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Calendar;
import java.util.Date;
@Setter
@Getter
public class TurAemJcrContent {
    @JsonProperty("cq:allowedTemplates")
    private String allowedTemplates;
    @JsonProperty("cq:conf")
    private String conf;
    @JsonProperty("cq:lastModified")
    @JsonFormat(locale = "en", shape = JsonFormat.Shape.STRING, pattern = "E MMM dd yyyy HH:mm:ss 'GMT'Z", timezone="GMT")
    private Date lastModified;
    @JsonProperty("cq:lastModifiedBy")
    private String lastModifiedBy;
    @JsonProperty("cq:redirectTarget")
    private String redirectTarget;
    @JsonProperty("cq:template")
    private String template;
    @JsonProperty("jcr:created")
    @JsonFormat(locale = "en", shape = JsonFormat.Shape.STRING, pattern = "E MMM dd yyyy HH:mm:ss 'GMT'Z", timezone="GMT")
    private Date created;
    @JsonProperty("jcr:createdBy")
    private String createdBy;
    @JsonProperty("jcr:title")
    private String title;
    @JsonProperty("pwaCachestrategy")
    private String pwaCacheStrategy;
    private String pwaDisplay;
    @JsonProperty("sling:configRef")
    private String slingConfigRef;
    @JsonProperty("sling:redirect")
    private boolean slingRedirect;
    @JsonProperty("sling:redirectStatus")
    private int slingRedirectStatus;
    @JsonProperty("sling:resourceType")
    private String slingResourceType;
}
