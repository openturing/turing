package com.viglet.turing.connector.webcrawler.ext;

import com.viglet.turing.connector.webcrawler.TurWCContext;
import org.jsoup.nodes.Document;

import java.util.Optional;

public interface TurWCExtInterface {
    Optional<String> consume(TurWCContext context);
}
