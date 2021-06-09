/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.wem.util;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.ref.ChannelRef;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.common.ref.SiteRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.AuthorizationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.Channel;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.as.client.javabean.ManagedObject;
import com.vignette.as.client.javabean.Site;
import com.vignette.logging.context.ContextLogger;

public class ETLTuringTranslator {

	IHandlerConfiguration config;

	private static final ContextLogger log = ContextLogger.getLogger(ETLTuringTranslator.class);

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
		String siteUrl = getSiteUrl(mo);
		if (siteUrl != null)
			return siteUrl + moFurlName;
		else
			return null;

	}

	private String translateChannel(ManagedObject mo) throws ApplicationException, ValidationException {
		String chFurlName;
		String moFurlName;
		if (log.isDebugEnabled()) {
			log.debug("ETLTuringTranslator MO: Channel");
		}
		Channel channel = (Channel) mo;
		chFurlName = channelBreadcrumb(channel);

		moFurlName = normalizeText(chFurlName);
		return moFurlName;
	}

	private String translateContentInstance(ManagedObject mo)
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

		List<String> siteNames = new ArrayList<String>();

		for (ChannelRef channelRef : channelRefs) {
			this.getSiteNames(siteNames, channelRef.getChannel());

		}

		Channel chosenChannel = getChosenChannel(channelRefs, siteNames);
		if (chosenChannel != null)
			chFurlName = channelBreadcrumb(chosenChannel);
		moFurlName = normalizeText(chFurlName + ciFurlName);
		return moFurlName;
	}

	private Channel getChosenChannel(ChannelRef[] channelRefs, List<String> siteNames) throws ApplicationException, ValidationException, RemoteException {
		String siteName = null;
		if (!siteNames.isEmpty())
			siteName = this.chosenSite(siteNames);
		
		boolean foundSite = false;
		Channel chosenChannel = null;
		if (siteName != null) {
			chosenChannel = channelsFromChosenSite(channelRefs, siteName, foundSite, chosenChannel);
		} else if (channelRefs != null && channelRefs.length > 0) {
			chosenChannel = channelRefs[0].getChannel();

		}
		return chosenChannel;
	}

	private Channel channelsFromChosenSite(ChannelRef[] channelRefs, String siteName, boolean foundSite,
			Channel chosenChannel) throws ApplicationException, ValidationException, RemoteException {
		for (ChannelRef channelRef : channelRefs) {
			for (SiteRef siteRef : channelRef.getChannel().getSiteRefs()) {
				if (!foundSite && siteRef.getSite().getName().equals(siteName)) {
					chosenChannel = channelRef.getChannel();
					foundSite = true;
				}
			}
		}
		if (!foundSite) {
			chosenChannel = channelRefs[0].getChannel();
		}
		return chosenChannel;
	}

	private String channelBreadcrumb(Channel channel) throws ApplicationException, ValidationException {
		if (channel != null) {
			StringBuilder channelPath = new StringBuilder();
			String chFurlName;
			Channel[] breadcrumb = channel.getBreadcrumbPath(true);
			for (int j = 0; j < breadcrumb.length; j++) {
				if (j > 0) {
					channelPath.append("/" + breadcrumb[j].getFurlName());
				}
			}
			channelPath.append("/");
			chFurlName = channelPath.toString();
			return chFurlName;
		} else {
			return "";
		}
	}

	public String normalizeText(String text) {
		return text.replace("-", "–").replace(" ", "-").replace("\\?", "%3F");
	}

	public String getSiteName(ManagedObject mo)
			throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
		ChannelRef[] channelRefs = null;
		List<String> siteNames = new ArrayList<String>();
		if (mo instanceof ContentInstance) {
			ContentInstance ci = (ContentInstance) mo;
			channelRefs = ci.getChannelAssociations();
			for (ChannelRef channelRef : channelRefs) {
				this.getSiteNames(siteNames, channelRef.getChannel());
			}
		} else if (mo instanceof Channel) {
			Channel channel = (Channel) mo;
			this.getSiteNames(siteNames, channel);
		}

		if (!siteNames.isEmpty()) {
			return this.chosenSite(siteNames);
		} else {
			log.info("ETLTuringTranslator Content without Site:" + mo.getName().toString());
			return null;
		}

	}

	private String chosenSite(List<String> siteNames) {
		String siteNameAssociated;
		if (config.getSitesAssocPriority() != null && !config.getSitesAssocPriority().isEmpty()) {
			boolean foundSite = false;
			String siteName = null;
			for (String siteAssocPriority : config.getSitesAssocPriority()) {
				if (!foundSite && siteNames.contains(siteAssocPriority)) {
					siteName = siteAssocPriority;
					foundSite = true;
				}
			}
			if (foundSite && siteName != null)
				siteNameAssociated = siteName;
			else
				siteNameAssociated = siteNames.get(0);
		} else
			siteNameAssociated = siteNames.get(0);
		return siteNameAssociated;
	}

	private void getSiteNames(List<String> siteNames, Channel channel) throws ApplicationException, RemoteException {
		List<Site> sites = this.getSitesFromChannel(channel);
		for (Site site : sites) {
			if (!siteNames.contains(site.getName())) {
				siteNames.add(site.getName());
			}
		}
	}

	private List<Site> getSitesFromChannel(Channel channel) throws ApplicationException, RemoteException {
		List<Site> sites = new ArrayList<Site>();
		if (channel != null) {
			for (SiteRef siteRef : channel.getSiteRefs()) {
				sites.add(siteRef.getSite());
			}
		}
		return sites;
	}

	public String getSiteDomain(ManagedObject mo)
			throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
		String siteName = this.getSiteName(mo);
		return getSiteDomainBySiteName(siteName);

	}

	public String getSiteDomainBySiteName(String siteName)
			throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
		if (log.isDebugEnabled()) {
			log.debug("ETLTuringTranslator getSiteUrl:" + siteName);
		}

		return config.getCDAURLPrefix(siteName);

	}

	public String getSiteUrl(ManagedObject mo)
			throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
		if (mo != null) {
			String siteNameAssociated = getSiteNameFromContentInstance(mo);
			if (siteNameAssociated != null) {
				return createSiteURL(mo, siteNameAssociated);
			} else {
				if (log.isDebugEnabled()) {
					log.debug("ETLTuringTranslator Content without channel:" + mo.getName().toString());
				}
				return null;
			}
		} else {
			if (log.isDebugEnabled()) {
				log.debug("ETLTuringTranslator Content is null");
			}
			return null;
		}

	}

	private String getSiteNameFromContentInstance(ManagedObject mo)
			throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
		String siteNameAssociated = null;
		ChannelRef[] channelRefs;
		if (mo instanceof ContentInstance) {
			ContentInstance ci = (ContentInstance) mo;
			channelRefs = ci.getChannelAssociations();

			List<String> siteNames = new ArrayList<String>();

			for (ChannelRef channelRef : channelRefs) {
				this.getSiteNames(siteNames, channelRef.getChannel());
			}

			if (!siteNames.isEmpty())
				siteNameAssociated = this.chosenSite(siteNames);
		}
		
		if (siteNameAssociated == null)
			siteNameAssociated = getSiteName(mo);
		
		return siteNameAssociated;
	}

	private String createSiteURL(ManagedObject mo, String siteNameAssociated)
			throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
		if (log.isDebugEnabled()) {
			log.debug("ETLTuringTranslator getSiteUrl:" + siteNameAssociated);
		}

		final String SLASH = "/";
		StringBuilder url = new StringBuilder(getSiteDomain(mo));

		if (config.getCDAContextName(siteNameAssociated) != null && config.hasContext(siteNameAssociated)) {
			url.append(SLASH);
			url.append(config.getCDAContextName(siteNameAssociated));
		}

		if (config.hasSiteName(siteNameAssociated) && normalizeText(siteNameAssociated) != null) {
			url.append(SLASH);
			url.append(normalizeText(siteNameAssociated));
		}

		if (config.hasFormat(siteNameAssociated) && config.getCDAFormatName(siteNameAssociated) != null) {
			url.append(SLASH);
			url.append(config.getCDAFormatName(siteNameAssociated));
		}

		return url.toString();
	}
}
