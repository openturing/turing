package com.viglet.turing.connector.aem.indexer.ext;

import java.lang.invoke.MethodHandles;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.viglet.turing.connector.aem.indexer.AemObject;
import com.viglet.turing.connector.cms.beans.TurMultiValue;
import com.viglet.turing.connector.cms.beans.TuringTag;
import com.viglet.turing.connector.cms.config.IHandlerConfiguration;
import com.viglet.turing.connector.cms.util.HtmlManipulator;

public class TurPageComponents implements ExtAttributeInterface {
	private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final String EMPTY_STRING = "";

	@Override
	public TurMultiValue consume(TuringTag tag, AemObject aemObject, IHandlerConfiguration config) {
		logger.debug("Executing TurPageComponents");

		StringBuffer components = new StringBuffer();
		try {
			getNode(aemObject.getJcrContentNode(), components);
			return TurMultiValue.singleItem(HtmlManipulator.html2Text(components.toString()));
		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
		return TurMultiValue.singleItem(EMPTY_STRING);
	}

	private void getNode(Node node, StringBuffer components) throws RepositoryException, ValueFormatException {

		if (node.hasNodes() && (node.getPath().startsWith("/content") || node.getPath().equals("/"))) {
			NodeIterator nodeIterator = node.getNodes();
			while (nodeIterator.hasNext()) {

				Node nodeChild = nodeIterator.nextNode();
				if (nodeChild.hasProperty("jcr:title"))
					components.append(AemObject.getJcrPropertyValue(nodeChild, "jcr:title"));
				if (nodeChild.hasProperty("text"))
					components.append(AemObject.getJcrPropertyValue(nodeChild, "text"));
				if (nodeChild.hasNodes()) {
					getNode(nodeChild, components);
				}
			}
		}
	}
}
