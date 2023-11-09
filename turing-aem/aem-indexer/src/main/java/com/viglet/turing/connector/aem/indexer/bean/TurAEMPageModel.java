package com.viglet.turing.connector.aem.indexer.bean;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
public class TurAEMPageModel {
    private String title;
    private String templateName;
    private String fragmentPath;
    private Date lastModifiedDate;
    private String language;
    @JsonProperty(":path")
    private String path;
    private String type = "cq:Page";
}
