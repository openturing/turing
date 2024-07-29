package com.viglet.turing.connector.aem.commons.bean;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.viglet.turing.connector.aem.commons.deserializer.TurAemUnixTimestamp;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class TurAemReplication {
    private int numQueue;
    @JsonDeserialize(using = TurAemUnixTimestamp.class)
    private Date published;
    private String publishedBy;
    private String action;

}
