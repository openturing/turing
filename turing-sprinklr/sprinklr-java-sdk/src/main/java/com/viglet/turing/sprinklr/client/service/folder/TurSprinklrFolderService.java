package com.viglet.turing.sprinklr.client.service.folder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.viglet.turing.sprinklr.client.service.TurSprinklrService;
import com.viglet.turing.sprinklr.client.service.folder.request.TurSprinklrFolderRequestBody;
import com.viglet.turing.sprinklr.client.service.folder.response.TurSprinklrFolderMapping;
import com.viglet.turing.sprinklr.client.service.folder.response.TurSprinklrFolderResult;
import com.viglet.turing.sprinklr.client.service.folder.response.TurSprinklrFolderSearch;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class TurSprinklrFolderService {
    public static final String FOLDER_SERVICE = "https://api2.sprinklr.com/%s/api/v2/folder/search";
    public static final int ROWS = 50;
    public static final String FOLDER_LIST = "folderList";
    public static final String APPLICATION_JSON = "application/json";
    TurSprinklrAccessToken turSprinklrAccessToken;
    LoadingCache<String, List<TurSprinklrFolderResult>> cachedFolderList;

    public TurSprinklrFolderService(TurSprinklrAccessToken turSprinklrAccessToken) {
        this.turSprinklrAccessToken = turSprinklrAccessToken;
        this.cachedFolderList = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build(
                        new CacheLoader<>() {
                            @NotNull
                            @Override
                            public List<TurSprinklrFolderResult> load(@NotNull String key) {
                                if (key.equals(FOLDER_LIST))
                                    return getTurSprinklrFolderResults();
                                else
                                    return new ArrayList<>();
                            }
                        });
    }

    public TurSprinklrFolderSearch run(int page) {
        try {
            return TurSprinklrService.executeService(TurSprinklrFolderSearch.class, turSprinklrAccessToken,
                    FOLDER_SERVICE.formatted(turSprinklrAccessToken.getEnvironment()),
                    RequestBody.create(new ObjectMapper().writeValueAsString(getRequestBody(page)),
                            MediaType.get(APPLICATION_JSON)));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public Optional<TurSprinklrFolderResult> getByCategoryId(String id) {

        try {
            List<TurSprinklrFolderResult> folderList = cachedFolderList.get(FOLDER_LIST);
            if (!folderList.isEmpty()) {
                for (TurSprinklrFolderResult result : folderList) {
                    if (hasMappingDetails(result)) {
                        return getTurSprinklrFolderResult(id, result);
                    }
                }
            }
        } catch (ExecutionException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();

    }

    private static Optional<TurSprinklrFolderResult> getTurSprinklrFolderResult(String id,
                                                                                TurSprinklrFolderResult result) {
        for (TurSprinklrFolderMapping mapping : result.getMappingDetails()) {
            if (mapping.getMappedCategoryIds().stream()
                    .anyMatch(categoryId -> categoryId.equals(id))) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    private static boolean hasMappingDetails(TurSprinklrFolderResult result) {
        return result != null &&
                result.getMappingDetails() != null && !result.getMappingDetails().isEmpty();
    }

    @NotNull
    private List<TurSprinklrFolderResult> getTurSprinklrFolderResults() {
        AtomicInteger page = new AtomicInteger(0);
        List<TurSprinklrFolderResult> result = new ArrayList<>();
        while (true) {
            TurSprinklrFolderSearch turSprinklrFolderSearch = run(page.get());
            if (turSprinklrFolderSearch.getData().getResult().isEmpty()) {
                break;
            } else {
                result.addAll(turSprinklrFolderSearch.getData().getResult());
            }

            page.incrementAndGet();
        }
        return result;
    }

    private static TurSprinklrFolderRequestBody getRequestBody(int page) {
        return TurSprinklrFolderRequestBody.builder()
                .start(page * ROWS)
                .rows(ROWS)
                .build();
    }


}
