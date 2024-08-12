package com.viglet.turing.sprinklr.client.service.kb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.sprinklr.client.service.TurSprinklrService;
import com.viglet.turing.sprinklr.client.service.kb.request.TurSprinklrKBPage;
import com.viglet.turing.sprinklr.client.service.kb.request.TurSprinklrKBRequestBody;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrKBSearch;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;

@Slf4j
public class TurSprinklrKBService {
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
