package com.viglet.turing.connector.webcrawler.ext;

import com.viglet.turing.connector.webcrawler.TurWCContext;
import org.jsoup.nodes.Document;

import java.util.Optional;

public class TurWCExtId implements TurWCExtInterface{

    @Override
    public Optional<String> consume(TurWCContext context) {
        return Optional.ofNullable(context.getUrl());
    }
}