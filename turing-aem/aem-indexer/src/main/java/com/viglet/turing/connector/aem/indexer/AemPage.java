package com.viglet.turing.connector.aem.indexer;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import static org.apache.jackrabbit.JcrConstants.JCR_CONTENT;

@Slf4j
@Getter
public class AemPage extends AemObject {
	private String title;
	private String description;
	private final StringBuffer components = new StringBuffer();

	public AemPage(Node node) {
		super(node);
		try {
			Node jcrContent = node.getNode(JCR_CONTENT);
			title = TurAemUtils.getJcrPropertyValue(jcrContent, "jcr:title");
			description = TurAemUtils.getJcrPropertyValue(jcrContent, "jcr:description");
			TurAemUtils.getNodeToComponent(jcrContent, components);
		} catch (RepositoryException e) {
			log.error(e.getMessage(), e);
		}
	}



}
