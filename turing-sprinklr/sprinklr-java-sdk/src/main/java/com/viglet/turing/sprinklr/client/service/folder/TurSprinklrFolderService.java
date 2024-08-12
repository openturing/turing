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
                                if (key.equals("folderList"))
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
                            MediaType.get("application/json")));
        } catch (JsonProcessingException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public Optional<TurSprinklrFolderResult> getByCategoryId(String id) {

        try {
            List<TurSprinklrFolderResult> folderList = cachedFolderList.get("folderList");
            if (!folderList.isEmpty()) {
                for (TurSprinklrFolderResult result : folderList) {
                    if (result != null &&
                            result.getMappingDetails() != null && !result.getMappingDetails().isEmpty()) {
                        for (TurSprinklrFolderMapping mapping : result.getMappingDetails()) {
                            if (mapping.getMappedCategoryIds().stream()
                                    .anyMatch(categoryId -> categoryId.equals(id))) {
                                return Optional.of(result);
                            }
                        }
                    }
                }
            }
        } catch (ExecutionException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();

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
