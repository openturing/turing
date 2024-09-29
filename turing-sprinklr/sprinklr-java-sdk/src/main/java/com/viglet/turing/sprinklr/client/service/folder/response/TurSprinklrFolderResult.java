/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.sprinklr.client.service.folder.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.viglet.turing.sprinklr.client.deserializer.TurSprinklrDates;
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
    private List<TurSprinklrFolderMapping> mappingDetails;
}
