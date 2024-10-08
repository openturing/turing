/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.connector.wem.util;

import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
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
