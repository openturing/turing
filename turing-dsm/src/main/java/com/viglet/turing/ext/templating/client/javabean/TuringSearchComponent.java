package com.viglet.turing.ext.templating.client.javabean;

import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.AuthorizationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.server.event.AsPrePersistenceEvent;
import com.vignette.ext.templating.util.RequestContext;
import com.vignette.logging.LoggingManager;
import com.vignette.logging.context.ContextLogger;

public abstract class TuringSearchComponent
extends com.vignette.ext.templating.client.javabean.ContentComponent
{

	private static final long serialVersionUID = 1L;
	public static final String ATTRIBUTE_MAX_RESULTS = "ResultsPerPage";
	public static final String ATTRIBUTE_RESULTS_PER_PAGE = "ResultsPerPage";
	public static final String ATTRIBUTE_ENDPOINT = "Endpoint";
	public static final String ATTRIBUTE_AC_ENDPOINT = "AutoCompleteEndpoint";
	public static final String ATTRIBUTE_KEYWORD = "keywordAttr";
	public static final String ATTRIBUTE_PAGE = "pageAttr";
	public static final String ATTRIBUTE_FACET = "facetAttr";
	public static final String ATTRIBUTE_ALLOWED_QS_ATTRS = "AllowedQSAttrs";
	public static final String DEFAULT_ATTRIBUTE_KEYWORD = "text";
	public static final String DEFAULT_ATTRIBUTE_PAGE = "page";
	public static final String DEFAULT_ATTRIBUTE_FACET = "facet";
	public static final String TUR_ATTR_QUERY = "q";
	public static final String TUR_ATTR_PAGE = "p";
	public static final String TUR_ATTR_FACET = "fq[]";
	public static final String ATTRIBUTE_CHANNEL_PATH = "ChannelPath";
	public static final String ATTRIBUTE_TITLE = "vgnExtTemplatingComponentTitle";
	public static final String ATTRIBUTE_HEADER = "vgnExtTemplatingComponentHeader";
	public static final String ATTRIBUTE_FOOTER = "vgnExtTemplatingComponentFooter";

	private static ContextLogger logger = LoggingManager.getContextLogger(TuringSearchComponent.class);

	@Override
	public void handlePreCreate(AsPrePersistenceEvent event)
			throws ApplicationException, AuthorizationException, ValidationException
	{
		if (logger.isTraceEnabled()) {
			logger.trace("Entered method handlePreCreate() with event : " + event);
		}
		super.handlePreCreate(event);
		if (logger.isTraceEnabled()) {
			logger.trace("Exit method handlePreCreate() with event : " + event);
		}
	}
	
	@Override
	public void handlePreUpdate(AsPrePersistenceEvent event)
			throws ApplicationException, AuthorizationException, ValidationException
	{
		if (logger.isTraceEnabled()) {
			logger.trace("Entered method handlePreUpdate() with event : " + event);
		}
		super.handlePreUpdate(event);
		if (logger.isTraceEnabled()) {
			logger.trace("Exit method handlePreUpdate() with event : " + event);
		}
	}

	@Override
	public String toXML(RequestContext requestContext)
			throws ApplicationException
	{
		if (logger.isTraceEnabled()) {
			logger.trace("Entered method toXML() with requestContext : " + requestContext);
		}
		String xml = "";
		if (logger.isTraceEnabled()) {
			logger.trace("Exit method toXML() with xml : " + xml);
		}
		return xml;
	}

	@Override
	public long getComponentDefaultTTL()
	{
		if (logger.isTraceEnabled()) {
			logger.trace("Exit method getComponentDefaultTTL() ");
		}
		long ttl = super.getComponentDefaultTTL();
		if (logger.isTraceEnabled()) {
			logger.trace("Exit method getComponentDefaultTTL() with ttl : " + ttl);
		}
		return ttl;
	}
}
