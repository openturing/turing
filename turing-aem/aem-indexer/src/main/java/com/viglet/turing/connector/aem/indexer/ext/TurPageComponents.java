package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.aem.indexer.TurAemUtils;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import com.viglet.turing.connector.cms.util.HtmlManipulator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurPageComponents implements ExtAttributeInterface {
	@Override
	public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
								 AemObject aemObject, IHandlerConfiguration config) {
		log.debug("Executing TurPageComponents");
		StringBuffer components = new StringBuffer();
		TurAemUtils.getJsonNodeToComponent(aemObject.getJcrContentNode(), components);
		return TurMultiValue.singleItem(HtmlManipulator.html2Text(components.toString()));
	}
}
