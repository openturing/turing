package com.viglet.turing.connector.sprinklr.ext;

import com.viglet.turing.connector.sprinklr.TurSprinklrContext;

import java.util.Locale;

public class TurSprinklrExtLocale implements TurSprinklrExtLocaleInterface {

    @Override
    public Locale consume(TurSprinklrContext context) {
        return context.getSearchResult().getLocale();
    }
}
