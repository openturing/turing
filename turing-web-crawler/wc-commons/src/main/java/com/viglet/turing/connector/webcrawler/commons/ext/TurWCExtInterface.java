package com.viglet.turing.connector.webcrawler.commons.ext;

import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.webcrawler.commons.TurWCContext;
import java.util.Optional;

public interface TurWCExtInterface {
    Optional<TurMultiValue> consume(TurWCContext context);
}
