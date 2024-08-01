package com.viglet.turing.connector.sprinklr.bean;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.viglet.turing.connector.sprinklr.deserializer.TurSprinklrDates;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class TurSprinklrSearchResult {
    private String id;
    private int version;
    private List<Integer> contributors;
    private List<String> tags;
    private TurSprinklrContent content;
    private boolean publicContent;
    private boolean hasConditionalSection;
    private boolean externalContent;
    private String originType;
    private boolean favourite;
    @JsonDeserialize(using = TurSprinklrDates.class)
    private Date publishingDate;
    private Object lngVariants;
    private Object inactiveLngVariants;
    private Object countryVariants;
    private TurSprinklrStats stats;
}
