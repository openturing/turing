package com.viglet.turing.connector.aem.sample.ext;

import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.TurAemExtDeltaDateInterface;


import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

public class TurAemExtSampleDeltaDate implements TurAemExtDeltaDateInterface {
    @Override
    public Date consume(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return Optional.ofNullable(aemObject.getLastModified())
                .map(Calendar::getTime).orElse(null);
    }
}
