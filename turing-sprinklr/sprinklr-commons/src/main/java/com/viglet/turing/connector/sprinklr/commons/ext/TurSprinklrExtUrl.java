package com.viglet.turing.connector.sprinklr.commons.ext;

import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;
import com.viglet.turing.sprinklr.client.service.kb.response.TurSprinklrSearchResult;

import java.util.Optional;

public class TurSprinklrExtUrl implements TurSprinklrExtInterface {

    @Override
    public Optional<TurMultiValue> consume(TurSprinklrContext context) {
        return Optional.of(Optional.ofNullable(context.getSearchResult())
                .map(TurSprinklrSearchResult::getMappingDetails)
                .filter(turSprinklrMappings -> !turSprinklrMappings.isEmpty())
                .map(turSprinklrMappings ->
                        TurMultiValue.singleItem(turSprinklrMappings.getFirst().getCommunityPermalink()))
                .orElse(TurMultiValue.empty()));

    }
}
