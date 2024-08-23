package com.viglet.turing.connector.sprinklr.sample.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;
import com.viglet.turing.connector.sprinklr.commons.ext.TurSprinklrExtInterface;

import java.util.Optional;

public class TurSprinklrExtSampleTitle implements TurSprinklrExtInterface {

    @Override
    public Optional<TurMultiValue> consume(TurSprinklrContext context) {
        return Optional.of(TurMultiValue.singleItem(context.getSearchResult().getContent().getTitle()));
    }
}
