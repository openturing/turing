package com.viglet.turing.connector.sprinklr.service.folder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.viglet.turing.connector.sprinklr.commons.bean.folder.TurSprinklrFolderSearch;
import com.viglet.turing.connector.sprinklr.service.TurSprinklrService;
import com.viglet.turing.connector.sprinklr.service.folder.request.TurSprinklrFolderRequestBody;
import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class TurSprinklrFolderService {
    public static final String FOLDER_SERVICE = "https://api2.sprinklr.com/%s/api/v2/folder/search";
    private final TurSprinklrService turSprinklrFolderService;

    @Inject
    public TurSprinklrFolderService(TurSprinklrService turSprinklrFolderService) {
        this.turSprinklrFolderService = turSprinklrFolderService;
    }

    public TurSprinklrFolderSearch run(TurSprinklrSource turSprinklrSource, int page) {
        try {
            return turSprinklrFolderService.executeService(TurSprinklrFolderSearch.class, turSprinklrSource,
                    FOLDER_SERVICE.formatted(turSprinklrSource.getEnvironment()),
                    RequestBody.create(new ObjectMapper().writeValueAsString(getRequestBody(page)),
                            MediaType.get("application/json")));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private static TurSprinklrFolderRequestBody getRequestBody(int page) {
        return TurSprinklrFolderRequestBody.builder()
                .start(page)
                .rows(50)
                .build();
    }


}
