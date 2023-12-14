package com.viglet.turing.connector.aem.indexer.ext;

import java.lang.invoke.MethodHandles;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
@Slf4j
public class TurSiteName implements ExtAttributeInterface {
	private static final String EMPTY_STRING = "";

	@Override
	public TurMultiValue consume(TuringTag tag, AemObject aemObject, IHandlerConfiguration config) {
		log.debug("Executing TurSiteName");

		return TurMultiValue.singleItem(EMPTY_STRING);

	}
}
