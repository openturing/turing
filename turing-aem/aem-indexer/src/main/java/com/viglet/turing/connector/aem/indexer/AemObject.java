package com.viglet.turing.connector.aem.indexer;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AemObject {
	protected static String JCR_CONTENT = "jcr:content";
	private static Logger logger = LoggerFactory.getLogger(AemObject.class);
	private Map<String,Object> attributes = new HashMap<>();
	public Map<String, Object> getAttributes() {
		return attributes;
	}
	public AemObject(Node node) {

		Node jcrContent;
		try {
		jcrContent = node.getNode(JCR_CONTENT);
		PropertyIterator jcrContentProperties = jcrContent.getProperties();
		while (jcrContentProperties.hasNext()) {
			Property property = jcrContentProperties.nextProperty();
			if (property.isMultiple())
				attributes.put(property.getName(), property.getValues()[0].getString());
			else
				attributes.put(property.getName(), property.getValue().getString());
		}
		} catch (PathNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}

	}
}
