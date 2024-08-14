package com.viglet.turing.connector.aem.commons.bean;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.viglet.turing.connector.aem.commons.deserializer.TurAemDates;
import com.viglet.turing.connector.aem.commons.deserializer.TurAemUnixTimestamp;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class TurAemContentTag {
    private String tag;
    private String tagID;
    private String name;
    private String title;
    private String description;
    private String titlePath;
    private int count;
    @JsonDeserialize(using = TurAemUnixTimestamp.class)
    private Date lastModified;
    private String lastModifiedBy;
    @JsonDeserialize(using = TurAemDates.class)
    private Date pubDate;
    private String publisher;
    private TurAemReplication replication;


}
