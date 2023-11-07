package com.viglet.turing.connector.aem.indexer;

import lombok.Getter;
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
@Getter
public class AemObject {
	private static final Logger logger = LoggerFactory.getLogger(AemObject.class);
	private Calendar lastModified;
	private Calendar createdDate;
	private boolean contentFragment = false;
	private boolean delivered = false;
	private String type;
	private String url;
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
	public AemObject(String nodePath) {
		this.url = nodePath + ".html";
	}
	public AemObject(Node node, String dataPath) {
		try {
			this.node = node;
			this.url = node.getPath() + ".html";
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
}
