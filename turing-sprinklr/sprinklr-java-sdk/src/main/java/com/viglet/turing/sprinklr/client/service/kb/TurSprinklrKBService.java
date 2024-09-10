package com.viglet.turing.sprinklr.client.service.kb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.sprinklr.client.service.TurSprinklrService;
import com.viglet.turing.sprinklr.client.service.kb.request.TurSprinklrKBFilter;
import com.viglet.turing.sprinklr.client.service.kb.request.TurSprinklrKBPage;
import com.viglet.turing.sprinklr.client.service.kb.request.TurSprinklrKBRequestBody;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrKBSearch;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;

import java.util.ArrayList;
import java.util.List;

/***
 * Use this class to send a search Knowledge Base request
 */
@Slf4j
public class TurSprinklrKBService {
    // https://developer.sprinklr.com/docs/read/api_20/knowledgebase_api/Search_Knowledge_Base_Content
    public static final String KB_SERVICE = "https://api2.sprinklr.com/%s/api/v2/knowledgebase/search";

    public static TurSprinklrKBSearch run(TurSprinklrAccessToken turSprinklrAccessToken, int page) {
        if (turSprinklrAccessToken != null) {
            try {
                String responseBody = new ObjectMapper().writeValueAsString(getRequestBody(page));
                return TurSprinklrService.executeService(TurSprinklrKBSearch.class, turSprinklrAccessToken,
                        KB_SERVICE.formatted(turSprinklrAccessToken.getEnvironment()),
                        RequestBody.create(responseBody,
                                MediaType.get("application/json")));
            } catch (JsonProcessingException e) {
                log.error(e.getMessage(), e);

            }
        }
        return null;
    }

    // Configures the request body
    private static TurSprinklrKBRequestBody getRequestBody(int page) {

        // Scheme for PUBLIC_CONTENT SCHEME filter
        final TurSprinklrKBFilter publicContentFilter = TurSprinklrKBFilter.builder()
                .filterType(TurSprinklrKBFilter.FilterType.IN)
                .field(TurSprinklrKBFilter.Field.PUBLIC_CONTENT)
                .values(List.of("true")).build();

        // Scheme for CONTENT_STATUS filter
        final TurSprinklrKBFilter contentStatusFilter = TurSprinklrKBFilter
                .builder()
                .filterType(TurSprinklrKBFilter.FilterType.IN)
                .field(TurSprinklrKBFilter.Field.KB_CONTENT_STATUS)
                .values(List.of("APPROVED")).build();

        final List<TurSprinklrKBFilter> filters = new ArrayList<>(2);
        filters.add(contentStatusFilter);
        filters.add(publicContentFilter);

        filters.forEach(filter -> log.info(filter.toString()));

        return TurSprinklrKBRequestBody.builder()
                .page(getPage(page)).filters(filters).build();
    }

    private static TurSprinklrKBPage getPage(int page) {
        return TurSprinklrKBPage.builder()
                .size(50)
                .page(page).build();
    }


}
