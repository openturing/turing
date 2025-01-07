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

package com.viglet.turing.connector.sprinklr.commons.kb.response;

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
