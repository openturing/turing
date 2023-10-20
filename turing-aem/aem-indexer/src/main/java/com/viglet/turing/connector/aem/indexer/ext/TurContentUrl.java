package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.lang.invoke.MethodHandles;

public class TurContentUrl implements ExtAttributeInterface {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final String EMPTY_STRING = "";

	@Override
	public TurMultiValue consume(TuringTag tag, AemObject aemObject, IHandlerConfiguration config) {
		logger.debug("Executing TurContentUrl");
		try {
			return TurMultiValue
					.singleItem(getURL(aemObject, config));
		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
		return TurMultiValue.singleItem(EMPTY_STRING);

	}

	public static String getURL(AemObject aemObject, IHandlerConfiguration config) throws RepositoryException {
		return String.format("%s%s.html", config.getCDAURLPrefix(), aemObject.getNode().getPath());
	}
}
