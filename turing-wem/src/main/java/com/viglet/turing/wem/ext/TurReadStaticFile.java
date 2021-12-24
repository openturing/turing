package com.viglet.turing.wem.ext;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.viglet.turing.wem.beans.TurMultiValue;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.as.client.javabean.StaticFile;
import com.vignette.as.config.ConfigUtil;
import com.vignette.as.config.component.AppSvcsComponent;
import com.vignette.config.client.common.ConfigException;
import com.vignette.config.client.common.ConfigRuntimeException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

import com.vignette.logging.context.ContextLogger;


public class TurReadStaticFile implements ExtAttributeInterface {
	private static final ContextLogger logger = ContextLogger.getLogger(TurChannelDescription.class);
	private static final String EMPTY_STRING = "";


	@Override
	public TurMultiValue consume(TuringTag tag, ContentInstance ci, AttributeData attributeData, IHandlerConfiguration config) throws Exception {
		if (log.isDebugEnabled()) {
			log.debug("Executing TurReadStaticFile");
		}

		TurMultiValue turMultiValue = new TurMultiValue();

		if (attributeData != null && attributeData.getValue() != null) {
			StaticFile staticFile = (StaticFile) ManagedObject
					.findByContentManagementId(new ManagedObjectVCMRef(attributeData.getValue().toString()));
			turMultiValue.add(readFile(config.getFileSourcePath() + staticFile.getPlacementPath()));
		}
		else {
			turMultiValue.add(EMPTY_STRING);
		}

		return turMultiValue;
	}

	private String readFile(String filePath) {
		log.debug("File Path: " + filePath);
		try {
			File file = new File(filePath);
			if (file.exists()) {
				InputStream inputStream = new FileInputStream(file);

				AutoDetectParser parser = new AutoDetectParser();
				// -1 = no limit of number of characters
				BodyContentHandler handler = new BodyContentHandler(-1);
				Metadata metadata = new Metadata();

				ParseContext pcontext = new ParseContext();

				parser.parse(inputStream, handler, metadata, pcontext);
				log.debug("File Content: " + handler.toString());
				return cleanTextContent(handler.toString());
			} else {
				logger.info("File not exists: " + filePath);
			}
		} catch (IOException | SAXException | TikaException e) {
			logger.error("readFile Exception", e);
		}

		return null;

	}

	private static String cleanTextContent(String text) {
		text = text.replaceAll("[\r\n\t]", " ");
		text = text.replaceAll("[^\\p{L}&&[^0-9A-Za-z]&&[^\\p{javaSpaceChar}]&&[^\\p{Punct}]]", "").replaceAll("_{2,}",
				"");
		// Remove 2 or more spaces
		text = text.trim().replaceAll(" +", " ");
		return text.trim();
	}

}
