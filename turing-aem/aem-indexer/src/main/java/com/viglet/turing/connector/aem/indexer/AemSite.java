package com.viglet.turing.connector.aem.indexer;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AemSite extends AemObject {
	private static Logger logger = LoggerFactory.getLogger(AemSite.class);
	
	private String title;
	private String url;
	
	private Map<String,Object> attributes = new HashMap<>();
	public AemSite(Node node) {
		super(node);
		try {
			Node jcrContent = node.getNode(JCR_CONTENT);
			url = node.getPath() + ".html";
			if (jcrContent.hasProperty("jcr:title"))
				title = jcrContent.getProperty("jcr:title").getString();
		} catch (PathNotFoundException e) {
			logger.error(e.getMessage(), e);
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

	public Map<String, Object> getAttributes() {
		return attributes;
	}

}
