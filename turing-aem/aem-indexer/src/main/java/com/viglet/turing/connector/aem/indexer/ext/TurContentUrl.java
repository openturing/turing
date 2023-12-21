package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import lombok.extern.slf4j.Slf4j;
@Slf4j
public class TurContentUrl implements ExtAttributeInterface {
	@Override
	public TurMultiValue consume(TuringTag tag, AemObject aemObject, IHandlerConfiguration config) {
		log.debug("Executing TurContentUrl");
		return TurMultiValue
				.singleItem(getURL(aemObject, config));

	}

	public static String getURL(AemObject aemObject, IHandlerConfiguration config) {
		return String.format("%s%s.html", config.getCDAURLPrefix(), aemObject.getPath());
	}
}
