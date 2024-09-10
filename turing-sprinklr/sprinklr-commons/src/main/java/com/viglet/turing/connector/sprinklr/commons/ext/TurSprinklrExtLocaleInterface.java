package com.viglet.turing.connector.sprinklr.commons.ext;

import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;

import java.util.Locale;

public interface TurSprinklrExtLocaleInterface {
    /**
     * Do something with Sprinklr Context to return a Locale.
     */
    Locale consume(TurSprinklrContext context);
}
