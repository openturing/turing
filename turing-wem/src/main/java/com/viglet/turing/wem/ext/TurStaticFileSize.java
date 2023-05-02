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

import java.io.File;
import java.lang.invoke.MethodHandles;


public class TurStaticFileSize implements ExtAttributeInterface {
    private static final ContextLogger logger = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());
    private static final String EMPTY_STRING = "";
    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
                                 IHandlerConfiguration config) throws Exception {
        logger.debug("Executing TurStaticFileSize");
        if (attributeData != null && attributeData.getValue() != null) {
            StaticFile staticFile = (StaticFile) ManagedObject
                    .findByContentManagementId(new ManagedObjectVCMRef(attributeData.getValue().toString()));

            File file = new File(config.getFileSourcePath().concat(staticFile.getPlacementPath()));
            logger.info(file.getAbsolutePath());
            return TurMultiValue.singleItem(file.exists() ? Long.toString(file.length()) : "0");
        } else {
            return TurMultiValue.singleItem(EMPTY_STRING);
        }

    }
}
