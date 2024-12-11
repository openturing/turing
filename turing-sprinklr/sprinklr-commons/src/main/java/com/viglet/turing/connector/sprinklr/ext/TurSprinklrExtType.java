package com.viglet.turing.connector.sprinklr.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;

import java.util.Optional;

public class TurSprinklrExtType implements TurSprinklrExtInterface {

    @Override
    public Optional<TurMultiValue> consume(TurSprinklrContext context) {
        return Optional.of(TurMultiValue.singleItem("%s:%s"
                .formatted(context.getSearchResult().getContent().getContentType(),
                        context.getSearchResult().getContent().getContentSubType())));
    }
}
