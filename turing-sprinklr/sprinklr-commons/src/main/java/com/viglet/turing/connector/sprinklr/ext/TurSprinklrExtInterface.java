package com.viglet.turing.connector.sprinklr.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.sprinklr.TurSprinklrContext;

import java.util.Optional;

public interface TurSprinklrExtInterface {
    Optional<TurMultiValue> consume(TurSprinklrContext context);
}
