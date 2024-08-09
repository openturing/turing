package com.viglet.turing.connector.sprinklr.commons.bean.folder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.viglet.turing.connector.sprinklr.commons.deserializer.TurSprinklrDates;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class TurSprinklrFolderResult {
    private String id;
    private String name;
    private String thumbnail;
    private String parentId;
    private List<String> path;
    private List<String> assetClasses;
    private List<String> tags;
    private boolean confidential;
    private boolean favourite;
    private boolean markPublic;
    private boolean disableChildSharing;
    private List<TurSprinklrShareConfig> shareConfigs;
    private List<String> grants;
    private int clientId;
    private int ownerUserId;
    @JsonDeserialize(using = TurSprinklrDates.class)
    private Date createdTime;
    @JsonDeserialize(using = TurSprinklrDates.class)
    private Date modifiedTime;
    private boolean deleted;
    private boolean canEdit;
}
