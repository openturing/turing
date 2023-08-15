package com.viglet.turing.connector.aem.indexer;

import java.util.HashMap;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AemPage extends AemObject {
	private static Logger logger = LoggerFactory.getLogger(AemPage.class);
	
	private String title;
	private String description;
	private String url;
	private String subTitle;
	private String lastModified;
	private Map<String,Object> attributes = new HashMap<>();
	public AemPage(Node node) {
		super(node);
		Node jcrContent;
		try {
			jcrContent = node.getNode(JCR_CONTENT);
			url = node.getPath() + ".html";

			if (jcrContent.hasProperty("pageTitle"))
				title = jcrContent.getProperty("pageTitle").getString();
			
			if (jcrContent.hasProperty("jcr:description")) 
				description = jcrContent.getProperty("jcr:description").getString();
			
		} catch (PathNotFoundException e) {
			logger.error(e.getMessage(), e);
		} catch (RepositoryException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getUrl() {
		return url;
	}

	public String getSubTitle() {
		return subTitle;
	}

	public String getLastModified() {
		return lastModified;
	}

	public Map<String, Object> getAttributes() {
		return attributes;
	}

}
