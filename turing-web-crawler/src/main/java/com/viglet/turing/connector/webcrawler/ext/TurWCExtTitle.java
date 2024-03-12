package com.viglet.turing.connector.webcrawler.ext;

import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.webcrawler.TurWCContext;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class TurWCExtTitle implements TurWCExtInterface{

    @Override
    public Optional<TurMultiValue> consume(TurWCContext context) {
        return Optional.of(TurMultiValue.singleItem(context.getDocument().title()));
    }
}
