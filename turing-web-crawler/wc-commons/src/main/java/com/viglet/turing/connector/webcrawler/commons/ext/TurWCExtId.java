package com.viglet.turing.connector.webcrawler.commons.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.webcrawler.commons.TurWCContext;

import java.util.Optional;

public class TurWCExtId implements TurWCExtInterface{

    @Override
    public Optional<TurMultiValue> consume(TurWCContext context) {
        return Optional.of(TurMultiValue.singleItem(context.getUrl()));
    }
}
