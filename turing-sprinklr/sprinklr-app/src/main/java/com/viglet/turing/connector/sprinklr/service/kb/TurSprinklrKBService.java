package com.viglet.turing.connector.sprinklr.service.kb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.connector.sprinklr.commons.bean.kb.TurSprinklrKBSearch;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import com.viglet.turing.connector.sprinklr.service.TurSprinklrService;
import com.viglet.turing.connector.sprinklr.service.kb.request.TurSprinklrKBPage;
import com.viglet.turing.connector.sprinklr.service.kb.request.TurSprinklrKBRequestBody;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TurSprinklrKBService {
    public static final String KB_SERVICE = "https://api2.sprinklr.com/%s/api/v2/knowledgebase/search";

    private final TurSprinklrService turSprinklrKBSearchService;

    @Inject
    public TurSprinklrKBService(TurSprinklrService turSprinklrKBSearchService) {
        this.turSprinklrKBSearchService = turSprinklrKBSearchService;

    }

    public TurSprinklrKBSearch run(TurSprinklrSource turSprinklrSource, int page) {
        try {
            String responseBody = new ObjectMapper().writeValueAsString(getRequestBody(page));
            return turSprinklrKBSearchService.executeService(TurSprinklrKBSearch.class, turSprinklrSource,
                    KB_SERVICE.formatted(turSprinklrSource.getEnvironment()),
                    RequestBody.create(responseBody,
                            MediaType.get("application/json")));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private static TurSprinklrKBRequestBody getRequestBody(int page) {
        return TurSprinklrKBRequestBody.builder()
                .page(getPage(page)).build();
    }

    private static TurSprinklrKBPage getPage(int page) {
        return TurSprinklrKBPage.builder()
                .size(50)
                .page(page).build();
    }


}
