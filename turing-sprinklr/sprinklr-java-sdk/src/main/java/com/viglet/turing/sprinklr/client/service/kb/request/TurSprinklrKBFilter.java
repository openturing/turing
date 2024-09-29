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

package com.viglet.turing.sprinklr.client.service.kb.request;

import lombok.*;

import java.util.List;

@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor(force = true)
@Setter
@Getter
@ToString
public class TurSprinklrKBFilter {
    private final FilterType filterType;
    private final Field field;
    private final List<String> values;

    public enum FilterType {
        AND,
        OR,
        NOT,
        IN,
        GT,
        GTE,
        LT,
        LTE,
        NIN,
        EQUALS,
        NOT_EQUALS,
        CONTAINS
    }

    public enum Field {
        KB_CONTRIBUTOR,
        KBA_CONTENT_ID,
        KB_CONTENT_TYPE,
        KB_CONTENT_SUB_TYPE,
        KB_CONTENT_STATUS,
        KB_TAGS,
        PUBLIC_CONTENT,
        MAPPED_PROJECT_ID,
        KB_FAVOURITE,
        KB_CREATED_TIME,
        KB_MODIFIED_TIME,
        KB_ORIGIN_TYPE,
        KB_MIGRATED_ID,
        KB_MIGRATED_FROM,
        KB_EXPORT_IMPORT_ID,
        KB_BASE_LNG_CONTENT_ID,
        KB_BASE_COUNTRY_CONTENT_ID,
        KB_CONTENT_SCHEDULED_STATUS,
        KB_MAP_SCHEDULED_DATE,
        KB_UN_MAP_SCHEDULED_DATE,
        KB_LINKED_ASSET_ID,
        KB_TITLE,
        KB_MARK_UP_TEXT
    }
}
