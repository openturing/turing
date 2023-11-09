package com.viglet.turing.aem.service.core.rest;

import com.viglet.turing.aem.service.core.utils.TurAemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static org.apache.jackrabbit.JcrConstants.*;

public class AemObject {
	private static final Logger logger = LoggerFactory.getLogger(AemObject.class);
	private Calendar lastModified;
	private Calendar createdDate;
	private boolean contentFragment = false;
	private boolean delivered = false;
	private String type;
	private String model;
	private Node node;
	private Node jcrContentNode;

	private final Map<String, Property> attributes = new HashMap<>();

	public AemObject(Node node) {
		this(node, null);
	}

	public static final String CONTENT_FRAGMENT = "contentFragment";
	public static final String CQ_IS_DELIVERED = "cq:isDelivered";
	public static final String CQ_LAST_MODIFIED = "cq:lastModified";
	public static final String CQ_MODEL = "cq:model";
	public AemObject(Node node, String dataPath) {
		try {
			this.node = node;
			type = node.getProperty(JCR_PRIMARYTYPE).getString();
			jcrContentNode = node.getNode(JCR_CONTENT);
			if (TurAemUtils.hasProperty(jcrContentNode,CQ_LAST_MODIFIED))
				lastModified = jcrContentNode.getProperty(CQ_LAST_MODIFIED).getDate();
			if (lastModified == null && TurAemUtils.hasProperty(jcrContentNode, JCR_LASTMODIFIED)) {
				lastModified = jcrContentNode.getProperty(JCR_LASTMODIFIED).getDate();
			}
			if (TurAemUtils.hasProperty(jcrContentNode, CONTENT_FRAGMENT)) {
				contentFragment = jcrContentNode.getProperty(CONTENT_FRAGMENT).getBoolean();
			}
			if (TurAemUtils.hasProperty(jcrContentNode, CQ_IS_DELIVERED)) {
				delivered = jcrContentNode.getProperty(CQ_IS_DELIVERED).getBoolean();
			}
			if (TurAemUtils.hasProperty(node,JCR_CREATED))
				createdDate = node.getProperty(JCR_CREATED).getDate();
			Node jcrDataRootNode = jcrContentNode.getNode("data");
			if (TurAemUtils.hasProperty(jcrDataRootNode, CQ_MODEL)) {
				model = jcrDataRootNode.getProperty(CQ_MODEL).getString();
			}

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

	public void setLastModified(Calendar lastModified) {
		this.lastModified = lastModified;
	}

	public Calendar getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(Calendar createdDate) {
		this.createdDate = createdDate;
	}

	public boolean isContentFragment() {
		return contentFragment;
	}

	public void setContentFragment(boolean contentFragment) {
		this.contentFragment = contentFragment;
	}

	public boolean isDelivered() {
		return delivered;
	}

	public void setDelivered(boolean delivered) {
		this.delivered = delivered;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Node getNode() {
		return node;
	}

	public void setNode(Node node) {
		this.node = node;
	}

	public Node getJcrContentNode() {
		return jcrContentNode;
	}

	public void setJcrContentNode(Node jcrContentNode) {
		this.jcrContentNode = jcrContentNode;
	}

	public Map<String, Property> getAttributes() {
		return attributes;
	}
}
