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
	private static Logger logger = LoggerFactory.getLogger(AemObject.class);
	public static String JCR_CONTENT = "jcr:content";
	private Calendar lastModified;
	private Calendar createdDate;
	private String type;
	private Node node;
	private Node jcrContentNode;
	private Map<String, Object> attributes = new HashMap<>();

	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public AemObject(Node node) {
		try {
			this.node = node;
			type = node.getProperty("jcr:primaryType").getString();
			jcrContentNode = node.getNode(JCR_CONTENT);
			if (jcrContentNode.hasProperty("cq:lastModified") && jcrContentNode.getProperty("cq:lastModified") != null)
				lastModified = jcrContentNode.getProperty("cq:lastModified").getDate();
			if (jcrContentNode.hasProperty("jcr:created") && jcrContentNode.getProperty("jcr:created") != null)
				createdDate = jcrContentNode.getProperty("jcr:created").getDate();
			PropertyIterator jcrContentProperties = jcrContentNode.getProperties();
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

	public static String getPropertyValue(Property property) throws RepositoryException, ValueFormatException {
		if (property.isMultiple())
			return property.getValues().length > 0 ? property.getValues()[0].getString() : "";
		else
			return property.getValue().getString();
	}
	public static String getJcrPropertyValue(Node node, String propertyName)
			throws RepositoryException, ValueFormatException, PathNotFoundException {
		if (node.hasProperty(propertyName))
			return getPropertyValue(node.getProperty(propertyName));
		return null;
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

	public Node getNode() {
		return node;
	}

	public Node getJcrContentNode() {
		return jcrContentNode;
	}

}
