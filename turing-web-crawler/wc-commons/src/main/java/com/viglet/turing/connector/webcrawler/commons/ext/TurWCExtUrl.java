package com.viglet.turing.connector.webcrawler.commons.ext;

import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.webcrawler.commons.TurWCContext;

import java.util.Optional;

public class TurWCExtUrl implements TurWCExtInterface{

    @Override
    public Optional<TurMultiValue> consume(TurWCContext context) {
        return Optional.of(TurMultiValue.singleItem(context.getUrl()));
    }
}