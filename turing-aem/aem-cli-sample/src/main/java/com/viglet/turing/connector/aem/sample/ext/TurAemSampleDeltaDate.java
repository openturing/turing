package com.viglet.turing.connector.aem.sample.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtDeltaDateInterface;


import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

public class TurAemSampleDeltaDate implements ExtDeltaDateInterface {
    @Override
    public Date consume(AemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return Optional.ofNullable(aemObject.getLastModified())
                .map(Calendar::getTime).orElse(null);
    }
}
