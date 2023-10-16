package com.viglet.turing.connector.aem.indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import static org.apache.jackrabbit.JcrConstants.JCR_CONTENT;

public class AemSite extends AemObject {
	private static final Logger logger = LoggerFactory.getLogger(AemSite.class);
	
	private String title;
	private String url;
	
	public AemSite(Node node) {
		super(node);
		try {
			Node jcrContent = node.getNode(JCR_CONTENT);
			url = node.getPath() + ".html";
			if (jcrContent.hasProperty("jcr:title"))
				title = jcrContent.getProperty("jcr:title").getString();
		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	public String getTitle() {
		return title;
	}


	public String getUrl() {
		return url;
	}

}
