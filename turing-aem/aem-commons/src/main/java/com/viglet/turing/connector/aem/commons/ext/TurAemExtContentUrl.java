package com.viglet.turing.connector.aem.commons.ext;

import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurAemExtContentUrl implements TurAemExtAttributeInterface {
    public static String getURL(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return String.format("%s%s.html", turAemSourceContext.getUrlPrefix(), aemObject.getPath());
    }

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurAemExtContentUrl");
        return TurMultiValue
                .singleItem(getURL(aemObject, turAemSourceContext));
    }
}
