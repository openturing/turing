package com.viglet.turing.connector.sprinklr.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.sprinklr.TurSprinklrContext;
import com.viglet.turing.sprinklr.client.service.folder.TurSprinklrFolderService;
import com.viglet.turing.sprinklr.client.service.token.TurSprinklrAccessToken;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class TurSprinklrExtCategory implements TurSprinklrExtInterface {

    @Override
    public Optional<TurMultiValue> consume(TurSprinklrContext context) {
        List<String> categories = context.getSearchResult().getMappingDetails().getFirst().getMappedCategoryIds();
        TurSprinklrAccessToken turSprinklrAccessToken = context.getAccessToken();
        TurSprinklrFolderService turSprinklrFolderService = new TurSprinklrFolderService(turSprinklrAccessToken);
        List<String> categoryNames = new ArrayList<>();
        if (!categories.isEmpty() && turSprinklrAccessToken != null) {
            categories.forEach(categoryId -> turSprinklrFolderService.getByCategoryId(categoryId)
                    .ifPresentOrElse(c -> categoryNames.add(c.getName()),
                            () -> categoryNames.add(categoryId)));
            return Optional.of(new TurMultiValue(categoryNames));
        } else {
            return Optional.empty();
        }
    }
}
