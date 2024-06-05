package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAemContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurContentUrl implements ExtAttributeInterface {
    public static String getURL(AemObject aemObject, IHandlerConfiguration config) {
        return String.format("%s%s.html", config.getCDAURLPrefix(), aemObject.getPath());
    }

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, IHandlerConfiguration config, TurAemContext turAemContext) {
        log.debug("Executing TurContentUrl");
        return TurMultiValue
                .singleItem(getURL(aemObject, config));
    }
}
