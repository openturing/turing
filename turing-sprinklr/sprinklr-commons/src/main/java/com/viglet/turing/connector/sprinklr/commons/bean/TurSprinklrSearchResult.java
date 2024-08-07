package com.viglet.turing.connector.sprinklr.commons.bean;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.viglet.turing.connector.sprinklr.commons.deserializer.TurSprinklrDates;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Locale;

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
    private String status;
    private boolean saveInLngVariantEnabled;
    private Locale locale;
    private boolean countryBaseContent;
    private String contentTemplateId;
    private List<TurSprinklrAsset> linkedAssets;
    private TurSprinklrTranslation translationProcess;
    private List<String> grants;
    private int clientId;
    private int ownerUserId;
    @JsonDeserialize(using = TurSprinklrDates.class)
    private Date createdTime;
    @JsonDeserialize(using = TurSprinklrDates.class)
    private Date modifiedTime;
    private int lastModifiedUserId;
    private boolean deleted;
    private TurSprinklrFolder folderMetadata;
    private boolean canEdit;
    private List<TurSprinklrMapping> mappingDetails;

}
