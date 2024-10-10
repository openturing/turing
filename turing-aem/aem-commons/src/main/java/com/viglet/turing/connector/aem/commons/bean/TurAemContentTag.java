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
