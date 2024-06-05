package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAemContext;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class TurAEMModificationDate implements ExtAttributeInterface {
    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, IHandlerConfiguration config, TurAemContext turAemContext) {
        log.debug("Executing TurAEMModificationDate");
        return Optional.ofNullable(aemObject.getLastModified())
                .map(lastModified -> TurMultiValue.singleItem(lastModified.getTime())).orElse(null);
    }
}
