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

import com.viglet.turing.client.sn.HttpTurSNServer;
import com.viglet.turing.client.sn.credentials.TurUsernamePasswordCredentials;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.beans.TuringTagMap;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.config.TurSNSiteConfig;
import com.vignette.as.client.common.*;
import com.vignette.as.client.common.ref.*;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.AuthorizationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.*;
import com.vignette.ext.furl.util.FurlUtil;
import com.vignette.ext.templating.util.ContentUtil;
import com.vignette.logging.context.ContextLogger;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;
import java.util.Map.Entry;

public class TuringUtils {
	private static final ContextLogger log = ContextLogger.getLogger(TuringUtils.class.getName());
	private static final String CTD_VGNEXTPAGE = "VgnExtPage";
	
	private TuringUtils() {
		throw new IllegalStateException("TuringUtils");
	}

	public static String listToString(List<String> stringList) {
		StringBuilder sb = new StringBuilder();
		int i = 0;
		for (String s : stringList) {
			if (i++ != stringList.size() - 1) {
				sb.append(s);
				sb.append(", ");
			}
		}
		return sb.toString();
	}

	// Old turIndexAttMapToSet
	public static Set<TuringTag> turingTagMapToSet(TuringTagMap turingTagMap) {
		Set<TuringTag> turingTags = new HashSet<TuringTag>();
		for (Entry<String, ArrayList<TuringTag>> entryCtd : turingTagMap.entrySet()) {
			for (TuringTag turingTag : entryCtd.getValue()) {
				turingTags.add(turingTag);
			}
		}
		return turingTags;
	}

	public static ContentInstance findContentInstanceByKey(ContentType contentType, String primaryKeyValue)
			throws Exception {
		ContentInstance ci = null;
		try {
			AttributeDefinitionData add = getKeyAttributeDefinitionData(contentType);
			DataType dt = add.getDataType();
			Object val = primaryKeyValue;
			if (dt.isInt() || dt.isNumerical() || dt.isTinyInt())
				val = Integer.valueOf(primaryKeyValue);
			ObjectTypeRef otr = new ObjectTypeRef(contentType);
			AttributeData atd = new AttributeData(add, val, otr);
			ManagedObjectRef ref = new ManagedObjectRef(otr, new AttributeData[] { atd });

			ci = (ContentInstance) ManagedObject.findById(ref);
		} catch (ApplicationException e) {
			log.error(e.getStackTrace());
		}

		return ci;
	}

	public static AttributeDefinitionData getKeyAttributeDefinitionData(ContentType ct) throws Exception {
		AttributeDefinitionData[] adds = ct.getData().getTopRelation().getKeyAttributeDefinitions();
		if (adds == null)
			throw new Exception("Failed to retrieve primary key definition", null);
		if (adds.length == 0)
			throw new Exception("No primary key found", null);
		if (adds.length > 1) {
			StringBuilder sb = new StringBuilder();
			sb.append("Works with one primary key only: ").append(adds.length);
			throw new Exception(sb.toString(), null);
		} else
			return adds[0];
	}

	public static void sendToTuring(TurSNJobItems turSNJobItems, IHandlerConfiguration config,
			TurSNSiteConfig turSNSiteConfig) {
		TurUsernamePasswordCredentials credentials = new TurUsernamePasswordCredentials(config.getLogin(),
				config.getPassword());
		try {
			HttpTurSNServer turSNServer = new HttpTurSNServer(new URL(config.getTuringURL()), turSNSiteConfig.getName(),
					turSNSiteConfig.getLocale(), credentials);
			TurSNJobUtils.importItems(turSNJobItems, turSNServer, false);
		} catch (MalformedURLException e) {
			log.error(e.getStackTrace());
		}
	}

	public static Channel getChosenChannel(ChannelRef[] channelRefs, List<String> siteNames,
			IHandlerConfiguration config) throws ApplicationException, ValidationException, RemoteException {
		String siteName = null;
		if (!siteNames.isEmpty())
			siteName = chosenSiteName(siteNames, config);
		Channel chosenChannel = null;
		if (siteName != null) {
			chosenChannel = channelsFromChosenSite(channelRefs, siteName, chosenChannel);
		} else if (channelRefs != null && channelRefs.length > 0) {
			chosenChannel = channelRefs[0].getChannel();
		}
		return chosenChannel;
	}

	private static Channel channelsFromChosenSite(ChannelRef[] channelRefs, String siteName, Channel chosenChannel)
			throws ApplicationException, ValidationException, RemoteException {
		boolean foundSite = false;
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

	public static String channelBreadcrumb(Channel channel, Locale locale)
			throws ApplicationException, ValidationException {
		if (channel != null) {
			AsLocaleRef asLocaleRef = null;
			if (locale != null) {
				try {
					asLocaleRef = new AsLocaleRef(locale);
				} catch (ValidationException var11) {
					if (log.isDebugEnabled()) {
						log.debug("Error occurred while creating AsLocaleRef object for locale: " + locale.toString());
					}
				}
			}

			StringBuilder channelPath = new StringBuilder();
			String chFurlName;
			Channel[] breadcrumb = channel.getBreadcrumbPath(true);
			for (int j = 0; j < breadcrumb.length; j++) {
				if (j > 0) {
					if (asLocaleRef != null) {
						ChannelLocalizedData chLocalData = breadcrumb[j].getData().getLocalizedData(asLocaleRef);
						if (chLocalData != null) {
							channelPath.append("/" + chLocalData.getFurlName());
						} else {
							channelPath.append("/" + breadcrumb[j].getFurlName());
						}
					} else {
						channelPath.append("/" + breadcrumb[j].getFurlName());
					}
				}
			}
			channelPath.append("/");
			chFurlName = channelPath.toString();
			return chFurlName;
		} else {
			return "";
		}
	}

	public static String normalizeText(String text) {
		return text.replace("-", "–").replace(" ", "-").replace("\\?", "%3F").replace("#", "%23");
	}

	public static Site getSite(ManagedObject mo, IHandlerConfiguration config) {
		List<Site> sites = new ArrayList<Site>();
		try {
			if (mo instanceof ContentInstance) {
				ContentInstance ci = (ContentInstance) mo;
				for (ChannelRef channelRef : ci.getChannelAssociations()) {
					sites.addAll(getSitesFromChannel(channelRef.getChannel()));
				}
			} else if (mo instanceof Channel) {
				Channel channel = (Channel) mo;
				sites = getSitesFromChannel(channel);
			}

			if (!sites.isEmpty()) {
				return chosenSite(sites, config);
			} else {
				if (mo != null) {
					log.info("ETLTuringTranslator Content without Site:" + mo.getName());
				} else {
					log.error("ManagedObject is null");
				}
			}
		} catch (ApplicationException e) {
			log.error(e.getMessage(), e);
		} catch (AuthorizationException e) {
			log.error(e.getMessage(), e);
		} catch (ValidationException e) {
			log.error(e.getMessage(), e);
		} catch (RemoteException e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	public static String getSiteName(ManagedObject mo, IHandlerConfiguration config) {
		List<String> siteNames = new ArrayList<String>();
		try {
			if (mo instanceof ContentInstance) {
				ContentInstance ci = (ContentInstance) mo;
				for (ChannelRef channelRef : ci.getChannelAssociations()) {
					getSiteNames(siteNames, channelRef.getChannel());
				}
			} else if (mo instanceof Channel) {
				getSiteNames(siteNames, (Channel) mo);
			}

			if (!siteNames.isEmpty()) {
				return chosenSiteName(siteNames, config);
			} else {
				log.info("ETLTuringTranslator Content without Site:" + mo.getName());
			}
		} catch (ApplicationException e) {
			log.error(e.getMessage(), e);
		} catch (AuthorizationException e) {
			log.error(e.getMessage(), e);
		} catch (ValidationException e) {
			log.error(e.getMessage(), e);
		} catch (RemoteException e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	private static Site chosenSite(List<Site> sites, IHandlerConfiguration config) {
		if (config.getSitesAssocPriority() != null && !config.getSitesAssocPriority().isEmpty()) {
			Site selectedSite = null;
			for (String siteAssocPriority : config.getSitesAssocPriority()) {
				for (Site site : sites) {
					try {
						if (site.getName().equals(siteAssocPriority)) {
							selectedSite = site;
						}
					} catch (ApplicationException e) {
						log.error(e);
					}
				}
			}
			if (selectedSite != null) {
				return selectedSite;
			}
		}
		if (sites.isEmpty()) {
			return null;
		} else {
			return sites.get(0);
		}
	}

	private static String chosenSiteName(List<String> siteNames, IHandlerConfiguration config) {
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
			if (foundSite && siteName != null) {
				siteNameAssociated = siteName;
			} else {
				siteNameAssociated = siteNames.get(0);
			}
		} else {
			siteNameAssociated = siteNames.get(0);
		}
		return siteNameAssociated;
	}

	public static void getSiteNames(List<String> siteNames, Channel channel)
			throws ApplicationException, RemoteException {
		List<Site> sites = getSitesFromChannel(channel);
		for (Site site : sites) {
			if (!siteNames.contains(site.getName())) {
				siteNames.add(site.getName());
			}
		}
	}

	private static List<Site> getSitesFromChannel(Channel channel) throws ApplicationException, RemoteException {
		List<Site> sites = new ArrayList<Site>();
		if (channel != null) {
			for (SiteRef siteRef : channel.getSiteRefs()) {
				sites.add(siteRef.getSite());
			}
		}
		return sites;
	}

	public static String getSiteDomain(ManagedObject mo, IHandlerConfiguration config) {
		String siteName = getSiteName(mo, config);
		return getSiteDomainBySiteName(siteName, config);
	}

	public static String getSiteDomainBySiteName(String siteName, IHandlerConfiguration config) {
		if (log.isDebugEnabled()) {
			log.debug("ETLTuringTranslator getSiteUrl:" + siteName);
		}

		return config.getCDAURLPrefix(siteName);
	}

	public static String getSiteUrl(ManagedObject mo, IHandlerConfiguration config) throws ApplicationException {
		if (mo != null) {
			String siteNameAssociated = getSiteNameFromContentInstance(mo, config);
			if (siteNameAssociated != null) {
				return createSiteURL(mo, config);
			} else {
				if (log.isDebugEnabled()) {
					log.debug("ETLTuringTranslator Content without channel:" + mo.getName());
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

	public static String getSiteNameFromContentInstance(ManagedObject mo, IHandlerConfiguration config) {
		return getSiteName(mo, config);
	}

	private static String createSiteURL(ManagedObject mo, IHandlerConfiguration config) throws ApplicationException {
		Site site = getSite(mo, config);
		String siteName = site.getName();
		if (log.isDebugEnabled()) {
			log.debug("ETLTuringTranslator createSiteURL:" + siteName);
		}
		final String SLASH = "/";
		StringBuilder url = new StringBuilder(getSiteDomain(mo, config));

		if (config.getCDAContextName(siteName) != null && FurlUtil.isIncludeContextName(site)) {
			url.append(SLASH);
			url.append(config.getCDAContextName(siteName));
		}

		if (FurlUtil.isIncludeSiteName(site) && normalizeText(siteName) != null) {
			url.append(SLASH);
			url.append(normalizeText(siteName));
		}

		if (FurlUtil.isFormatIncluded(site)) {
			url.append(SLASH);
			url.append(ContentUtil.getDefaultFormatForSite(getSite(mo, config)));
		}
		if (FurlUtil.isIncludeLocaleName(site)) {
			String locale = getLocale(mo, config);
			if (locale != null) {
				url.append(SLASH);
				url.append(locale);
			}
		}
		return url.toString();
	}

	public static String getLocale(ManagedObject mo, IHandlerConfiguration config) {
		String locale = null;
		if (mo != null && mo.getLocale() != null && mo.getLocale().getJavaLocale() != null) {
			locale = mo.getLocale().getJavaLocale().toString();
		}

		if (locale == null) {
			return getDefaultLocale(mo, config);
		} else {
			return locale;
		}
	}

	private static String getDefaultLocale(ManagedObject mo, IHandlerConfiguration config) {
		try {
			Site site = getSite(mo, config);
			AsLocale asLocale = ContentUtil.getDefaultLocaleForSite(site);
			if (asLocale != null && asLocale.getJavaLocale() != null) {
				return asLocale.getJavaLocale().toString();
			}
		} catch (ApplicationException e) {
			log.error(e.getMessage(), e);
		}
		return null;
	}

	public static AsLocaleData getAsLocaleDataFromManagedObject(ManagedObjectVCMRef managedObjectVCMRef) {
		ManagedObject mo;
		AsLocaleData asLocaleData = null;
		try {
			mo = managedObjectVCMRef.retrieveManagedObject();

			if (mo != null && mo.getLocale() != null && mo.getLocale().getAsLocale() != null
					&& mo.getLocale().getAsLocale().getData() != null)
				asLocaleData = mo.getLocale().getAsLocale().getData();
		} catch (ApplicationException e) {
			log.error(e.getMessage(), e);
		} catch (RemoteException e) {
			log.error(e.getMessage(), e);
		}
		return asLocaleData;
	}

	public static String getSiteNameFromManagedObjectVCMRef(ManagedObjectVCMRef managedObjectVCMRef,
			IHandlerConfiguration config) {
		String siteName = null;
		try {
			siteName = TuringUtils.getSiteName(managedObjectVCMRef.retrieveManagedObject(), config);
		} catch (ApplicationException e) {
			log.error(e.getMessage(), e);
		} catch (RemoteException e) {
			log.error(e.getMessage(), e);
		}
		return siteName;
	}

	public static Channel getParentChannelFromBreadcrumb(Channel[] breadcrumb) {
		try {
			ManagedObject managedObject = breadcrumb[breadcrumb.length - 1].getContentManagementId()
					.retrieveManagedObject();
			if (managedObject instanceof Channel) {
				return (Channel) breadcrumb[breadcrumb.length - 1].getContentManagementId().retrieveManagedObject();
			}
		} catch (ApplicationException e) {
			log.error(e.getMessage(), e);
		} catch (RemoteException e) {
			log.error(e.getMessage(), e);
		}
		return new Channel();
	}

	public static List<ManagedObject> getVgnExtPagesFromChannel(Channel channel) {
		List<ManagedObject> vgnExtPageList = new ArrayList<ManagedObject>();
		try {
			for (Map.Entry<String, Set<ComputedMoReferenceInstance>> computedReferenceInstances : channel
					.getComputedReferenceInstances().entrySet()) {
				for (ComputedMoReferenceInstance computedMoReferenceInstance : computedReferenceInstances.getValue()) {
					ManagedObjectVCMRef managedObjectVCMRef = new ManagedObjectVCMRef(
							computedMoReferenceInstance.getReferentId());
					ManagedObject moReference = managedObjectVCMRef.retrieveManagedObject();
					if (moReference instanceof ContentInstance
							&& moReference.getObjectType().getData().getName().equals(CTD_VGNEXTPAGE)) {
						vgnExtPageList.add(moReference);
					}
				}
			}
		} catch (ApplicationException e) {
			log.error(e.getMessage(), e);
		} catch (AuthorizationException e) {
			log.error(e.getMessage(), e);
		} catch (ValidationException e) {
			log.error(e.getMessage(), e);
		} catch (RemoteException e) {
			log.error(e.getMessage(), e);
		}

		return vgnExtPageList;
	}
}
