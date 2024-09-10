package com.viglet.turing.connector.sprinklr.commons.ext;

import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;

import java.util.Locale;

public class TurSprinklrExtLocale implements TurSprinklrExtLocaleInterface {

    /**
     * Gets locale from Sprinklr Context
     */
    @Override
    public Locale consume(TurSprinklrContext context) {
        return context.getSearchResult().getLocale();
    }
}
