package com.viglet.turing.connector.aem.indexer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import static org.apache.jackrabbit.JcrConstants.JCR_CONTENT;
@Getter
@Slf4j
public class AemSite extends AemObject {

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
			log.error(e.getMessage(), e);
		}
	}


}
