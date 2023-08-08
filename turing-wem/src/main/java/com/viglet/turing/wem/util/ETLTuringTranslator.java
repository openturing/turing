/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.wem.util;

import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.ref.ChannelRef;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.AuthorizationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.Channel;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.ext.templating.util.ContentUtil;
import com.vignette.logging.context.ContextLogger;

import java.lang.invoke.MethodHandles;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ETLTuringTranslator {

	IHandlerConfiguration config;

	private static final ContextLogger log = ContextLogger.getLogger(MethodHandles.lookup().lookupClass());

	public ETLTuringTranslator(IHandlerConfiguration config) {
		this.config = config;
	}

	public String translate(String attributeValue)
			throws ApplicationException, AuthorizationException, ValidationException, RemoteException {

		String href = null;
		String guid = null;

		Pattern hrefFinder = Pattern.compile("<a.*href=\"(.*?)\"", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
		Matcher regexMatcher = hrefFinder.matcher(attributeValue);

		while (regexMatcher.find()) {
			href = regexMatcher.group(1);
		}
		if (log.isDebugEnabled()) {
			log.error("ETLTuringTranslator Href: " + href);
		}
		if (href != null) {
			Pattern guidFinder = Pattern.compile(
					".*vgn_ext_templ_rewrite.*vgnextoid=(.*?)RCRD.*/vgn_ext_templ_rewrite.*",
					Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
			Matcher regexMatcherGUID = guidFinder.matcher(href);
			// vgn_ext_templ_rewrite?vgnextoid=616913074c0a3410VgnVCM1000003b74010aRCRD/vgn_ext_templ_rewrite
			while (regexMatcherGUID.find()) {
				guid = regexMatcherGUID.group(1);
			}

			if (log.isDebugEnabled()) {
				log.error("ETLOTSNTranslator GUID: " + guid);
			}
			if (guid != null) {
				guid += "RCRD";
				return translateByGUID(guid);
			} else {
				return href;
			}
		} else {
			return attributeValue;
		}

	}

	public String translateByGUID(String guid)
			throws ApplicationException, AuthorizationException, ValidationException, RemoteException {
		String moFurlName = "";
		ManagedObject mo = ManagedObject.findByContentManagementId(new ManagedObjectVCMRef(guid));
		if (mo instanceof ContentInstance) {
			moFurlName = translateContentInstance(mo);

		} else if (mo instanceof Channel) {
			moFurlName = translateChannel(mo);
		}
		String siteUrl = TuringUtils.getSiteUrl(mo, config);
		if (siteUrl != null)
			return siteUrl + moFurlName;
		else
			return null;

	}

	public String translateChannel(ManagedObject mo) throws ApplicationException, ValidationException {
		String chFurlName;
		String moFurlName;
		if (log.isDebugEnabled()) {
			log.debug("ETLTuringTranslator MO: Channel");
		}
		Channel channel = (Channel) mo;
		chFurlName = TuringUtils.channelBreadcrumb(channel, ContentUtil.getLocaleFromString(TuringUtils.getLocale(mo, config)));

		moFurlName = TuringUtils.normalizeText(chFurlName);
		return moFurlName;
	}

	public String translateContentInstance(ManagedObject mo)
			throws ApplicationException, RemoteException, ValidationException, AuthorizationException {
		String chFurlName = "";
		ChannelRef[] channelRefs;
		String ciFurlName;
		String moFurlName;
		if (log.isDebugEnabled()) {
			log.debug("ETLTuringTranslator MO: ContentInstance");
		}
		ContentInstance ci = (ContentInstance) mo;

		channelRefs = ci.getChannelAssociations();
		ciFurlName = ci.getFurlName();

		List<String> siteNames = new ArrayList<>();

		for (ChannelRef channelRef : channelRefs) {
			TuringUtils.getSiteNames(siteNames, channelRef.getChannel());
		}

		Channel chosenChannel = TuringUtils.getChosenChannel(channelRefs, siteNames, config);
		if (chosenChannel != null) {
			chFurlName = TuringUtils.channelBreadcrumb(chosenChannel, ContentUtil.getLocaleFromString(TuringUtils.getLocale(mo, config)));
		}
		moFurlName = TuringUtils.normalizeText(chFurlName + ciFurlName);
		return moFurlName;
	}
}
