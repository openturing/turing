package com.viglet.turing.connector.aem.indexer;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import static org.apache.jackrabbit.JcrConstants.JCR_CONTENT;

@Getter
public class AemPage extends AemObject {
	private static final Logger logger = LoggerFactory.getLogger(AemPage.class);

	private String title;
	private String description;
	private String subTitle;
	private final StringBuffer components = new StringBuffer();

	public AemPage(Node node) {
		super(node);
		try {
			Node jcrContent = node.getNode(JCR_CONTENT);
			title = getJcrPropertyValue(jcrContent, "jcr:title");
			description = getJcrPropertyValue(jcrContent, "jcr:description");
			getNode(jcrContent, components);
		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
	}


	private void getNode(Node node, StringBuffer components) throws RepositoryException, ValueFormatException {

		if (node.hasNodes() && (node.getPath().startsWith("/content") || node.getPath().equals("/"))) {
			NodeIterator nodeIterator = node.getNodes();
			while (nodeIterator.hasNext()) {

				Node nodeChild = nodeIterator.nextNode();
				if (nodeChild.hasProperty("jcr:title"))
					components.append(getJcrPropertyValue(nodeChild, "jcr:title"));
				if (nodeChild.hasProperty("text"))
					components.append(getJcrPropertyValue(nodeChild, "text"));
				if (nodeChild.hasNodes()) {
					getNode(nodeChild, components);
				}
			}
		}
	}
}
