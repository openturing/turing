package com.viglet.turing.connector.aem.indexer.ext;

import java.lang.invoke.MethodHandles;

import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;

public class TurContentUrl implements ExtAttributeInterface {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final String EMPTY_STRING = "";

	@Override
	public TurMultiValue consume(TuringTag tag, AemObject aemObject, IHandlerConfiguration config) {
		logger.debug("Executing TurContentUrl");
		try {
			return TurMultiValue
					.singleItem(String.format("%s%s.html", config.getCDAURLPrefix(), aemObject.getNode().getPath()));
		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
		return TurMultiValue.singleItem(EMPTY_STRING);

	}
}
