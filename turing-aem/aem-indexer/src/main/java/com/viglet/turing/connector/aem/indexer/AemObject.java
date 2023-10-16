package com.viglet.turing.connector.aem.indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AemObject {
	private static final Logger logger = LoggerFactory.getLogger(AemObject.class);
	public static String JCR_CONTENT = "jcr:content";
	private Calendar lastModified = null;
	private Calendar createdDate;
	private String type;
	private Node node;
	private Node jcrContentNode;
	private final Map<String, Property> attributes = new HashMap<>();

	public Map<String, Property> getAttributes() {
		return attributes;
	}

	public AemObject(Node node) {
		this(node, null);
	}

	private boolean hasProperty(Node node, String property) throws RepositoryException {
		return node.hasProperty(property) && node.getProperty(property) != null;
	}
	public AemObject(Node node, String dataPath) {
		try {
			this.node = node;
			type = node.getProperty("jcr:primaryType").getString();
			jcrContentNode = node.getNode(JCR_CONTENT);
			if (hasProperty(jcrContentNode,"cq:lastModified"))
				lastModified = jcrContentNode.getProperty("cq:lastModified").getDate();
			if (lastModified == null && hasProperty(jcrContentNode, "jcr:lastModified")) {
				lastModified = jcrContentNode.getProperty("jcr:lastModified").getDate();
			}
			if (hasProperty(node,"jcr:created"))
				createdDate = node.getProperty("jcr:created").getDate();
			if (dataPath != null) {
				Node jcrDataNode = jcrContentNode.getNode(dataPath);
				PropertyIterator jcrContentProperties = jcrDataNode.getProperties();
				while (jcrContentProperties.hasNext()) {
					Property property = jcrContentProperties.nextProperty();
					attributes.put(property.getName(), property);
				}
			}
		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
	}

	public static String getPropertyValue(Property property) {
		try {
			if (property.isMultiple())
				return property.getValues().length > 0 ? property.getValues()[0].getString() : "";
			else
				return property.getValue().getString();
		} catch (IllegalStateException | RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
		return null;
	}
	public static String getJcrPropertyValue(Node node, String propertyName)
			throws RepositoryException {
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
