package com.viglet.turing.connector.aem.commons.ext.customer;

import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtAttributeInterface;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class TurMapleType implements ExtAttributeInterface {
    public static final String DAM_MODELS = "/conf/maple-bear/settings/dam/cfm/models/";

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurMapleType");
        return Optional.ofNullable(aemObject.getModel()).map(model ->
                TurMultiValue.singleItem(switch (model) {
                    case DAM_MODELS + "postagem" -> "post";
                    case DAM_MODELS + "comunicado" -> "announcement";
                    case DAM_MODELS + "evento" -> "event";
                    default -> "";
                })).orElse(TurMultiValue.singleItem(""));
    }
}
