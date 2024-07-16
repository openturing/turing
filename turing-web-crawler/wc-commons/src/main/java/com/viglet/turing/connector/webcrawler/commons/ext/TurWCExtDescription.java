package com.viglet.turing.connector.webcrawler.commons.ext;

import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.webcrawler.commons.TurWCContext;

import java.util.Optional;

public class TurWCExtDescription implements TurWCExtInterface{

    public static final String META_NAME_DESCRIPTION = "meta[name=description]";
    public static final String CONTENT = "content";

    @Override
    public Optional<TurMultiValue> consume(TurWCContext context) {
        return Optional.of(context.getDocument().select(META_NAME_DESCRIPTION))
                .map(elements -> !elements.isEmpty() ? TurMultiValue.singleItem(elements.getFirst().attr(CONTENT)) : null);

    }
}
