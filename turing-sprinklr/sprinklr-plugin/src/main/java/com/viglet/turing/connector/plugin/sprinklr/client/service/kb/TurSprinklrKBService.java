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

package com.viglet.turing.connector.plugin.sprinklr.client.service.kb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.connector.plugin.sprinklr.client.service.TurSprinklrService;
import com.viglet.turing.connector.sprinklr.commons.kb.request.TurSprinklrKBFilter;
import com.viglet.turing.connector.sprinklr.commons.kb.request.TurSprinklrKBPage;
import com.viglet.turing.connector.sprinklr.commons.kb.request.TurSprinklrKBRequestBody;
import com.viglet.turing.connector.sprinklr.commons.kb.response.TurSprinklrKBSearch;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/***
 * Use this class to send a search Knowledge Base request
 */
@Slf4j
@Component
public class TurSprinklrKBService {
    public static final String PROD_2 = "prod2";
    private final TurSprinklrService turSprinklrService;
    // https://developer.sprinklr.com/docs/read/api_20/knowledgebase_api/Search_Knowledge_Base_Content
    public static final String KB_SERVICE = "https://api2.sprinklr.com/%s/api/v2/knowledgebase/search";
    public static final String TRUE = "true";
    public static final String APPROVED = "APPROVED";
    public static final int SIZE = 50;

    @Inject
    private TurSprinklrKBService(TurSprinklrService turSprinklrService) {
        this.turSprinklrService = turSprinklrService;
    }

    public TurSprinklrKBSearch run(int page) {
            try {
                String responseBody = new ObjectMapper().writeValueAsString(getRequestBody(page));
                return turSprinklrService.executeService(TurSprinklrKBSearch.class,
                        KB_SERVICE.formatted(PROD_2),
                        responseBody);
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);

            }
        return null;
    }

    // Configures the request body
    private static TurSprinklrKBRequestBody getRequestBody(int page) {

        // Scheme for PUBLIC_CONTENT SCHEME filter
        final TurSprinklrKBFilter publicContentFilter = TurSprinklrKBFilter.builder()
                .filterType(TurSprinklrKBFilter.FilterType.IN)
                .field(TurSprinklrKBFilter.Field.PUBLIC_CONTENT)
                .values(List.of(TRUE)).build();

        // Scheme for CONTENT_STATUS filter
        final TurSprinklrKBFilter contentStatusFilter = TurSprinklrKBFilter
                .builder()
                .filterType(TurSprinklrKBFilter.FilterType.IN)
                .field(TurSprinklrKBFilter.Field.KB_CONTENT_STATUS)
                .values(List.of(APPROVED)).build();

        final List<TurSprinklrKBFilter> filters = new ArrayList<>(2);
        filters.add(contentStatusFilter);
        filters.add(publicContentFilter);

        filters.forEach(filter -> log.info(filter.toString()));

        return TurSprinklrKBRequestBody.builder()
                .page(getPage(page)).filters(filters).build();
    }

    private static TurSprinklrKBPage getPage(int page) {
        return TurSprinklrKBPage.builder()
                .size(SIZE)
                .page(page).build();
    }


}
