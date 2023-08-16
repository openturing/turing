package com.viglet.turing.connector.aem.indexer;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AemObject {
	protected static String JCR_CONTENT = "jcr:content";
	private static Logger logger = LoggerFactory.getLogger(AemObject.class);
	private Calendar lastModified;
	private Calendar createdDate;
	private String type;
	private Map<String, Object> attributes = new HashMap<>();

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public AemObject(Node node) {
		try {
			type = node.getProperty("jcr:primaryType").getString();
			Node jcrContent = node.getNode(JCR_CONTENT);
			if (jcrContent.hasProperty("cq:lastModified") && jcrContent.getProperty("cq:lastModified") != null)
				lastModified = jcrContent.getProperty("cq:lastModified").getDate();
			if (jcrContent.hasProperty("jcr:created") && jcrContent.getProperty("jcr:created") != null)
				createdDate = jcrContent.getProperty("jcr:created").getDate();
			PropertyIterator jcrContentProperties = jcrContent.getProperties();
			while (jcrContentProperties.hasNext()) {
				Property property = jcrContentProperties.nextProperty();
				getPropertyValue(property);
			}
		} catch (PathNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
	}

	protected String getPropertyValue(Property property) throws RepositoryException, ValueFormatException {
		if (property.isMultiple())
			return property.getValues().length > 0 ? property.getValues()[0].getString() : "";
		else
			return property.getValue().getString();
	}

	public Calendar getLastModified() {
		return lastModified;
	}

	public Calendar getCreatedDate() {
		return createdDate;
	}

	public String getType() {
		return type;
	}

}
