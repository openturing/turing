package com.viglet.turing.ext.templating.client.javabean;

import com.vignette.as.client.api.beangen.ContentBeanMethod;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.ext.templating.cache.RenderedManagedObjectCacheKey;
import com.vignette.ext.templating.util.RequestContext;
import com.vignette.logging.LoggingManager;
import com.vignette.logging.context.ContextLogger;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.StringTokenizer;

public class TuringSearchToolbarComponent
extends TuringSearchComponent
{
	private static ContextLogger logger = LoggingManager.getContextLogger(TuringSearchToolbarComponent.class);
	private static final long serialVersionUID = 1L;
	private String keywordAttrName = null;
	private String allowedQSAttrs = null;
	
	public Collection<String> getRequiredRequestParameterNames(RequestContext rc)
			throws ApplicationException
	{
		Collection<String> requiredRequestParameterNames = new HashSet<>(Arrays.asList(new String[] { getKeywordAttrName(), "vgnextoid" }));
		addDisplayViewRequiredRequestParameterNames(requiredRequestParameterNames);
		return requiredRequestParameterNames;
	}
	
	public String getAutoCompleteEndPoint() throws ApplicationException {
		return (String) getAttributeValue(ATTRIBUTE_AC_ENDPOINT);
	}

	public String getAllowedQSAttrs(RequestContext rc) {
		if (null == allowedQSAttrs) {
			try {
				allowedQSAttrs = "";
				String separator = "";
				String allowedQSAttrsValue = (String) getAttributeValue(ATTRIBUTE_ALLOWED_QS_ATTRS);
				logger.error("TuringSearchToolbarComponent - getAllowedQSAttrs " + allowedQSAttrsValue);
				if (null != allowedQSAttrsValue)
				{
					StringTokenizer st = new StringTokenizer(allowedQSAttrsValue,",");
					while(st.hasMoreElements())
					{
						String attrName = st.nextToken();
						String attrValue = rc.getParameter(attrName);
						if (null != attrValue)
						{
							allowedQSAttrs += separator + attrName + "=" + attrValue;
							separator = "&";
						}
					}
				}
			} catch (ApplicationException e) {
				logger.error(e.getMessage(), e);
			}
		}
		logger.error("TuringSearchToolbarComponent - getAllowedQSAttrs " + allowedQSAttrs);
		return allowedQSAttrs;
	}
	

	@ContentBeanMethod
	public String getKeywordAttrName()
	{
		if (null == keywordAttrName)
		{
			try {
				keywordAttrName =  (String) getAttributeValue(ATTRIBUTE_KEYWORD);
				if (null == keywordAttrName)
					keywordAttrName = DEFAULT_ATTRIBUTE_KEYWORD;
			} catch (ApplicationException e) {
				logger.error(e.getMessage(), e);
			}
		}
		return keywordAttrName;
	}


	@Override
	public String createCacheKey(RenderedManagedObjectCacheKey key) throws ApplicationException {
		String cacheKey = super.createCacheKey(key);
		RequestContext rc = key.getRequestContext();
		cacheKey += "|" + getAllowedQSAttrs(rc);
		if (logger.isDebugEnabled())
			logger.debug("TuringSearchToolbarComponent - createCacheKey " + cacheKey);
		logger.error("TuringSearchToolbarComponent - createCacheKey " + cacheKey);
		return cacheKey;
	}



}
