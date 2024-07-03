package com.viglet.turing.connector.aem.commons.ext.customer;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurMapleRestrictedContent implements ExtAttributeInterface {
    private static boolean hasContentRestricted(AemObject aemObject) {
        return aemObject.getAttributes().containsKey("contentRestricted")
                && aemObject.getAttributes().get("contentRestricted").equals(true);
    }

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurMapleRestrictedContent");
        return TurMultiValue.singleItem(hasContentRestricted(aemObject));
    }
}
