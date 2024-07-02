package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAemContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurSiteName implements ExtAttributeInterface {
    private static final String EMPTY_STRING = "";

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, TurAemContext turAemContext) {
        log.debug("Executing TurSiteName");
        return TurMultiValue.singleItem(EMPTY_STRING);
    }
}
