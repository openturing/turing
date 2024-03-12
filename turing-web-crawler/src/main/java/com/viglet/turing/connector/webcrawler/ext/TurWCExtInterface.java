package com.viglet.turing.connector.webcrawler.ext;

import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.webcrawler.TurWCContext;
import org.jsoup.nodes.Document;

import java.util.Optional;

public interface TurWCExtInterface {
    Optional<TurMultiValue> consume(TurWCContext context);
}
