package com.viglet.turing.connector.sprinklr.commons.ext;

import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.sprinklr.commons.TurSprinklrContext;

import java.util.Optional;

public interface TurSprinklrExtInterface {
    Optional<TurMultiValue> consume(TurSprinklrContext context);
}