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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.connector.wem.beans.TuringTag;
import com.viglet.turing.connector.wem.beans.TuringTagMap;
import com.viglet.turing.connector.wem.config.IHandlerConfiguration;
import com.viglet.turing.connector.wem.config.TurSNSiteConfig;
import com.vignette.as.client.common.ChannelLocalizedData;
import com.vignette.as.client.common.ComputedMoReferenceInstance;
import com.vignette.as.client.common.ref.AsLocaleRef;
import com.vignette.as.client.common.ref.ChannelRef;
import com.vignette.as.client.common.ref.ManagedObjectVCMRef;
import com.vignette.as.client.common.ref.SiteRef;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.AuthorizationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.*;
import com.vignette.ext.templating.util.ContentUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.rmi.RemoteException;
import java.util.*;
import java.util.Map.Entry;

@Slf4j
public class TuringUtils {
    private static final String CTD_VGN_EXT_PAGE = "VgnExtPage";

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
        Set<TuringTag> turingTags = new HashSet<>();
        for (Entry<String, ArrayList<TuringTag>> entryCtd : turingTagMap.entrySet()) {
            turingTags.addAll(entryCtd.getValue());
        }
        return turingTags;
    }

    public static void basicAuth(IHandlerConfiguration config, HttpPost post) {
        if (config.getLogin() != null && !config.getLogin().trim().isEmpty()) {
            String auth = String.format("%s:%s", config.getLogin(), config.getPassword());
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + encodedAuth;
            post.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }
    }

    public static void sendToTuringAsZipFile(ByteArrayOutputStream byteArrayOutputStream, IHandlerConfiguration config,
                                             TurSNSiteConfig turSNSiteConfig) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(
                    String.format("%s/api/sn/%s/import/zip", config.getTuringURL(), turSNSiteConfig.getName()));
            byte[] bytes = byteArrayOutputStream.toByteArray();
            ContentBody contentBody = new InputStreamBody(new ByteArrayInputStream(bytes), "export.zip");
            HttpEntity entity = MultipartEntityBuilder.create().addPart("file", contentBody).build();
            httpPost.setEntity(entity);

            basicAuth(config, httpPost);
            try (CloseableHttpResponse response = client.execute(httpPost)) {

                if (log.isDebugEnabled()) {
                    log.debug("Viglet Turing Index Request URI: {}", httpPost.getURI());
                    log.debug("Viglet Turing indexer response HTTP result is: {}, for request uri: {}", response.getStatusLine().getStatusCode(), httpPost.getURI());
                    log.debug("Viglet Turing indexer response HTTP result is: {}", httpPost.getEntity().toString());
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static void sendToTuring(TurSNJobItems turSNJobItems, IHandlerConfiguration config,
                                    TurSNSiteConfig turSNSiteConfig) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            if (!turSNJobItems.getTuringDocuments().isEmpty()) {

                String encoding = StandardCharsets.UTF_8.name();

                ObjectMapper mapper = new ObjectMapper();
                String jsonResult = mapper.writeValueAsString(turSNJobItems);

                Charset utf8Charset = StandardCharsets.UTF_8;
                Charset customCharset = Charset.forName(encoding);

                ByteBuffer inputBuffer = ByteBuffer.wrap(jsonResult.getBytes());

                // decode UTF-8
                CharBuffer data = utf8Charset.decode(inputBuffer);

                // encode
                ByteBuffer outputBuffer = customCharset.encode(data);

                HttpPost httpPost = getHttpPost(config, turSNSiteConfig, outputBuffer);

                basicAuth(config, httpPost);
                try (CloseableHttpResponse response = client.execute(httpPost)) {

                    if (log.isDebugEnabled()) {
                        log.debug("Viglet Turing Index Request URI: {}", httpPost.getURI());
                        log.debug("JSON: {}", jsonResult);
                        log.debug("Viglet Turing indexer response HTTP result is: {}, for request uri: {}", response.getStatusLine().getStatusCode(), httpPost.getURI());
                        log.debug("Viglet Turing indexer response HTTP result is: {}", httpPost.getEntity().toString());
                    }
                    turSNJobItems.getTuringDocuments().clear();
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static HttpPost getHttpPost(IHandlerConfiguration config, TurSNSiteConfig turSNSiteConfig, ByteBuffer outputBuffer) {
        byte[] outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);
        String jsonUTF8 = new String(outputData);
        HttpPost httpPost = new HttpPost(
                String.format("%s/api/sn/%s/import", config.getTuringURL(), turSNSiteConfig.getName()));

        StringEntity entity = new StringEntity(jsonUTF8, StandardCharsets.UTF_8);
        httpPost.setEntity(entity);
        httpPost.setHeader("Accept", "application/json");
        httpPost.setHeader("Content-type", "application/json");
        httpPost.setHeader("Accept-Encoding", StandardCharsets.UTF_8.name());
        return httpPost;
    }

    public static Channel getChosenChannel(ChannelRef[] channelRefs, List<String> siteNames,
                                           IHandlerConfiguration config) throws ApplicationException, ValidationException, RemoteException {
        String siteName = null;
        if (!siteNames.isEmpty())
            siteName = chosenSiteName(siteNames, config);

        boolean foundSite = false;
        Channel chosenChannel = null;
        if (siteName != null) {
            chosenChannel = channelsFromChosenSite(channelRefs, siteName, foundSite, chosenChannel);
        } else if (channelRefs != null && channelRefs.length > 0) {
            chosenChannel = channelRefs[0].getChannel();

        }
        return chosenChannel;
    }

    private static Channel channelsFromChosenSite(ChannelRef[] channelRefs, String siteName, boolean foundSite,
                                                  Channel chosenChannel)
            throws ApplicationException, ValidationException, RemoteException {
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
            return getChannelPath(channel, getAsLocaleRef(locale)).toString();
        } else {
            return "";
        }
    }

    private static StringBuilder getChannelPath(Channel channel, AsLocaleRef asLocaleRef)
            throws ApplicationException, ValidationException {
        StringBuilder channelPath = new StringBuilder();
        Channel[] breadcrumb = channel.getBreadcrumbPath(true);
        for (int j = 0; j < breadcrumb.length; j++) {
            if (j > 0) {
                if (asLocaleRef != null) {
                    ChannelLocalizedData chLocalData = breadcrumb[j].getData().getLocalizedData(asLocaleRef);
                    if (chLocalData != null) {
                        channelPath.append("/").append(chLocalData.getFurlName());
                    } else {
                        channelPath.append("/").append(breadcrumb[j].getFurlName());
                    }
                } else {
                    channelPath.append("/").append(breadcrumb[j].getFurlName());
                }
            }
        }
        channelPath.append("/");
        return channelPath;
    }

    private static AsLocaleRef getAsLocaleRef(Locale locale) throws ApplicationException {
        AsLocaleRef asLocaleRef = null;
        if (locale != null) {
            try {
                asLocaleRef = new AsLocaleRef(locale);
            } catch (ValidationException var11) {
                if (log.isDebugEnabled()) {
                    log.debug("Error occurred while creating AsLocaleRef object for locale: {}", locale);
                }
            }
        }
        return asLocaleRef;
    }

    public static String normalizeText(String text) {
        return text.replace("-", "–").replace(" ", "-")
                .replace("\\?", "%3F").replace("#", "%23");
    }

    public static Site getSite(ManagedObject mo, IHandlerConfiguration config) {
        List<Site> sites = new ArrayList<>();
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
        } catch (ApplicationException | AuthorizationException | ValidationException | RemoteException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static String getSiteName(ManagedObject mo, IHandlerConfiguration config) {
        List<String> siteNames = new ArrayList<>();
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

        } catch (ApplicationException | AuthorizationException | ValidationException | RemoteException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private static Site chosenSite(List<Site> sites, IHandlerConfiguration config) {
        return Optional.ofNullable(getSiteFromPriority(sites, config))
                .orElseGet(() -> sites.isEmpty() ? null : sites.get(0));
    }

    private static Site getSiteFromPriority(List<Site> sites, IHandlerConfiguration config) {
        try {
            if (hasSitePriority(config)) {
                for (String siteAssocPriority : config.getSitesAssocPriority()) {
                    for (Site site : sites) {
                        if (site.getName().equals(siteAssocPriority)) {
                            return site;
                        }
                    }
                }
            }
        } catch (ApplicationException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private static boolean hasSitePriority(IHandlerConfiguration config) {
        return config.getSitesAssocPriority() != null && !config.getSitesAssocPriority().isEmpty();
    }

    private static String chosenSiteName(List<String> siteNames, IHandlerConfiguration config) {
        String siteNameAssociated;
        if (hasSitePriority(config)) {
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
        List<Site> sites = new ArrayList<>();
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
                    log.debug("ETLTuringTranslator Content without channel:{}", mo.getName());
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
        if (site != null) {
            String siteName = site.getName();
            if (log.isDebugEnabled()) {
                log.debug("ETLTuringTranslator getSiteUrl:" + siteName);
            }

            final String SLASH = "/";
            StringBuilder url = new StringBuilder(getSiteDomain(mo, config));

            if (config.getCDAContextName(siteName) != null) {
                url.append(SLASH);
                url.append(config.getCDAContextName(siteName));
            }

            normalizeText(siteName);
            url.append(SLASH);
            url.append(normalizeText(siteName));

            url.append(SLASH);
            url.append(ContentUtil.getDefaultFormatForSite(getSite(mo, config)));
            String locale = getLocale(mo, config);
            if (locale != null) {
                url.append(SLASH);
                url.append(locale);
            }

            return url.toString();
        }
        return "";
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
            if (site != null) {
                AsLocale asLocale = ContentUtil.getDefaultLocaleForSite(site);
                if (asLocale != null && asLocale.getJavaLocale() != null) {
                    return asLocale.getJavaLocale().toString();
                }
            }
        } catch (ApplicationException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public static Channel getParentChannelFromBreadcrumb(Channel[] breadcrumb) {
        try {
            ManagedObject managedObject = breadcrumb[breadcrumb.length - 1].getContentManagementId()
                    .retrieveManagedObject();
            if (managedObject instanceof Channel) {
                return (Channel) breadcrumb[breadcrumb.length - 1].getContentManagementId().retrieveManagedObject();
            }
        } catch (ApplicationException | RemoteException e) {
            log.error(e.getMessage(), e);
        }
        return new Channel();
    }

    public static List<ManagedObject> getVgnExtPagesFromChannel(Channel channel) {
        List<ManagedObject> vgnExtPageList = new ArrayList<>();
        try {
            for (Map.Entry<String, Set<ComputedMoReferenceInstance>> computedReferenceInstances : channel
                    .getComputedReferenceInstances().entrySet()) {
                for (ComputedMoReferenceInstance computedMoReferenceInstance : computedReferenceInstances.getValue()) {
                    ManagedObjectVCMRef managedObjectVCMRef = new ManagedObjectVCMRef(
                            computedMoReferenceInstance.getReferentId());
                    ManagedObject moReference = managedObjectVCMRef.retrieveManagedObject();
                    if (moReference instanceof ContentInstance
                            && moReference.getObjectType().getData().getName().equals(CTD_VGN_EXT_PAGE)) {
                        vgnExtPageList.add(moReference);
                    }
                }
            }
        } catch (ApplicationException | AuthorizationException | ValidationException | RemoteException e) {
            log.error(e.getMessage(), e);
        }

        return vgnExtPageList;
    }

}
