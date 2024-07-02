package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAemContext;
import com.viglet.turing.connector.aem.indexer.persistence.model.TurAemSource;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueList;

public interface ExtContentInterface {
    TurCmsTargetAttrValueList consume(AemObject aemObject, TurAemContext turAemContext);
}