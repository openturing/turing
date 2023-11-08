package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.util.HtmlManipulator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurPageComponents implements ExtAttributeInterface {

	@Override
	public TurMultiValue consume(TuringTag tag, AemObject aemObject, IHandlerConfiguration config) {
		log.debug("Executing TurPageComponents");

		// StringBuffer components = new StringBuffer();
		//	TurAemUtils.getNode(aemObject.getJcrContentNode(), components);
		return TurMultiValue.singleItem(HtmlManipulator.html2Text(aemObject.getJcrContentNode().toString()));
	}
}
