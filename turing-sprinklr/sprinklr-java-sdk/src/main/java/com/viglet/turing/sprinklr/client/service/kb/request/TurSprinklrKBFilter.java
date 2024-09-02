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
