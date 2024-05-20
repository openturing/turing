package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAemUtils;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import com.viglet.turing.connector.cms.util.HtmlManipulator;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

@Slf4j
public class TurPageComponents implements ExtAttributeInterface {

    public static final String RESPONSIVE_GRID = "responsivegrid";
    public static final String ROOT = "root";

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, IHandlerConfiguration config) {
        log.debug("Executing TurPageComponents");
        StringBuffer components = new StringBuffer();
        if (aemObject.getJcrContentNode() != null && aemObject.getJcrContentNode().has(ROOT)
                && aemObject.getJcrContentNode().get(ROOT) instanceof JSONObject root
                && root.has(RESPONSIVE_GRID)
                && root.get(RESPONSIVE_GRID) instanceof JSONObject responsiveGrid) {
            TurAemUtils.getJsonNodeToComponent(responsiveGrid, components);
        }
        return TurMultiValue.singleItem(HtmlManipulator.html2Text(components.toString()));
    }
}
