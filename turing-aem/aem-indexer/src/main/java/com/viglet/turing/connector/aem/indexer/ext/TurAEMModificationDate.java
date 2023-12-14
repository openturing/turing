package com.viglet.turing.connector.aem.indexer.ext;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
@Slf4j
public class TurAEMModificationDate implements ExtAttributeInterface {

	@Override
	public TurMultiValue consume(TuringTag tag, AemObject aemObject, IHandlerConfiguration config) {
		log.debug("Executing TurAEMModificationDate");
		TimeZone tz = TimeZone.getTimeZone("UTC");
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		df.setTimeZone(tz);
		
		return TurMultiValue.singleItem(aemObject.getLastModified() != null
				? df.format(aemObject.getLastModified().getTime()) : null);

	}
}
