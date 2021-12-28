package com.viglet.turing.wem.ext;

import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.as.client.javabean.StaticFile;
import com.vignette.logging.context.ContextLogger;


public class TurReadStaticFile implements ExtAttributeInterface {
    private static final ContextLogger logger = ContextLogger.getLogger(TurReadStaticFile.class);
    private static final String EMPTY_STRING = "";

    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData, IHandlerConfiguration config) throws Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("Executing TurReadStaticFile");
        }

        TurMultiValue turMultiValue = new TurMultiValue();

        if (attributeData != null && attributeData.getValue() != null) {
            StaticFile staticFile = (StaticFile) ManagedObject
                    .findByContentManagementId(new ManagedObjectVCMRef(attributeData.getValue().toString()));
            turMultiValue.add(String.format("file://%s%s", config.getFileSourcePath(), staticFile.getPlacementPath()));
        } else {
            turMultiValue.add(EMPTY_STRING);
        }

        return turMultiValue;
    }
}
