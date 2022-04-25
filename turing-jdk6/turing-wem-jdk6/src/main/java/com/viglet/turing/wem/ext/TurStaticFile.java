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


public class TurStaticFile implements ExtAttributeInterface {
    private static final ContextLogger logger = ContextLogger.getLogger(TurStaticFile.class.getName());
    private static final String EMPTY_STRING = "";
    private static final String FILE_PROTOCOL = "file://";

    @Override
    public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData,
                                 IHandlerConfiguration config) throws Exception {
        logger.debug("Executing TurReadStaticFile");
        if (attributeData != null && attributeData.getValue() != null) {
            StaticFile staticFile = (StaticFile) ManagedObject
                    .findByContentManagementId(new ManagedObjectVCMRef(attributeData.getValue().toString()));
            return TurMultiValue.singleItem(FILE_PROTOCOL
                    .concat(config.getFileSourcePath()).concat(staticFile.getPlacementPath()));
        } else {
            return TurMultiValue.singleItem(EMPTY_STRING);
        }

    }
}
