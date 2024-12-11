package com.viglet.turing.connector.sprinklr.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class TurSprinklrExtModificationDate implements TurSprinklrExtInterface {
    @Override
    public Optional<TurMultiValue> consume(TurSprinklrContext context) {
        return Optional.of(TurMultiValue.singleItem(context.getSearchResult().getModifiedTime()));
    }
}
