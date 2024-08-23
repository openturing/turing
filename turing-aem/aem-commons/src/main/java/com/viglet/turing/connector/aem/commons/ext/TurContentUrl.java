package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurContentUrl implements ExtAttributeInterface {
    public static String getURL(AemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return String.format("%s%s.html", turAemSourceContext.getUrlPrefix(), aemObject.getPath());
    }

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurContentUrl");
        return TurMultiValue
                .singleItem(getURL(aemObject, turAemSourceContext));
    }
}
