package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TurMapleType implements ExtAttributeInterface {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public TurMultiValue consume(TuringTag tag, AemObject aemObject, IHandlerConfiguration config) {
        logger.debug("Executing TurMapleType");
        String type = "";
        if (aemObject.getModel() != null) {
            switch (aemObject.getModel()) {
                case "/conf/maple-bear/settings/dam/cfm/models/postagem":
                    type = "post";
                    break;
                case "/conf/maple-bear/settings/dam/cfm/models/comunicado":
                    type = "announcement";
                    break;
                case "/conf/maple-bear/settings/dam/cfm/models/evento":
                    type = "event";
                    break;
            }
        }
        return TurMultiValue.singleItem(type);
    }
}