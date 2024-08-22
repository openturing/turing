package com.viglet.turing.connector.webcrawler.sample.ext;

import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.webcrawler.commons.TurWCContext;
import com.viglet.turing.connector.webcrawler.commons.ext.TurWCExtInterface;

import java.util.Optional;

public class TurWCExtSampleTitle implements TurWCExtInterface {
    @Override
    public Optional<TurMultiValue> consume(TurWCContext context) {
        return Optional.of(TurMultiValue.singleItem(context.getDocument().title()));
    }
}
