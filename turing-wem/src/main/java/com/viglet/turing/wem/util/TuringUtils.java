/*
 * Copyright (C) 2016-2021 Alexandre Oliveira <alexandre.oliveira@viglet.com>
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.wem.beans.TuringTag;
import com.viglet.turing.wem.beans.TuringTagMap;
import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.viglet.turing.wem.config.TurSNSiteConfig;
import com.vignette.as.client.common.AsLocaleData;
import com.vignette.as.client.common.AttributeData;
import com.vignette.as.client.common.AttributeDefinitionData;
import com.vignette.as.client.common.DataType;
import com.vignette.as.client.common.ref.*;
import com.vignette.as.client.exception.ApplicationException;
import com.vignette.as.client.exception.AuthorizationException;
import com.vignette.as.client.exception.ValidationException;
import com.vignette.as.client.javabean.*;
import com.vignette.ext.furl.util.FurlUtil;
import com.vignette.ext.templating.util.ContentUtil;
import com.vignette.logging.context.ContextLogger;
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

public class TuringUtils {
    private static final ContextLogger log = ContextLogger.getLogger(TuringUtils.class);

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
            ManagedObjectRef ref = new ManagedObjectRef(otr, new AttributeData[]{atd});

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

    public static void basicAuth(IHandlerConfiguration config, HttpPost post) {
        if (config.getLogin() != null && config.getLogin().trim().length() > 0) {
            String auth = String.format("%s:%s", config.getLogin(), config.getPassword());
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            String authHeader = "Basic " + encodedAuth;
            post.setHeader(HttpHeaders.AUTHORIZATION, authHeader);
        }
    }

    public static void sendToTuringAsZipFile(ByteArrayOutputStream byteArrayOutputStream, IHandlerConfiguration config, TurSNSiteConfig turSNSiteConfig) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(String.format("%s/api/sn/%s/import/zip", config.getTuringURL(),
                    turSNSiteConfig.getName()));
            byte[] bytes = byteArrayOutputStream.toByteArray();
            ContentBody contentBody = new InputStreamBody(new ByteArrayInputStream(bytes), "export.zip");
            HttpEntity entity = MultipartEntityBuilder.create().addPart("file", contentBody).build();
            httpPost.setEntity(entity);

            basicAuth(config, httpPost);
            try (CloseableHttpResponse response = client.execute(httpPost)) {

                if (log.isDebugEnabled()) {
                    log.debug(String.format("Viglet Turing Index Request URI: %s", httpPost.getURI()));
                    log.debug(
                            String.format("Viglet Turing indexer response HTTP result is: %s, for request uri: %s",
                                    response.getStatusLine().getStatusCode(), httpPost.getURI()));
                    log.debug(String.format("Viglet Turing indexer response HTTP result is: %s",
                            httpPost.getEntity().toString()));
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static void sendToTuring(TurSNJobItems turSNJobItems, IHandlerConfiguration config, TurSNSiteConfig turSNSiteConfig) {
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

                byte[] outputData = new String(outputBuffer.array()).getBytes(StandardCharsets.UTF_8);
                String jsonUTF8 = new String(outputData);
                HttpPost httpPost = new HttpPost(String.format("%s/api/sn/%s/import", config.getTuringURL(),
                        turSNSiteConfig.getName()));

                StringEntity entity = new StringEntity(jsonUTF8, StandardCharsets.UTF_8);
                httpPost.setEntity(entity);
                httpPost.setHeader("Accept", "application/json");
                httpPost.setHeader("Content-type", "application/json");
                httpPost.setHeader("Accept-Encoding", StandardCharsets.UTF_8.name());

                basicAuth(config, httpPost);
                try (CloseableHttpResponse response = client.execute(httpPost)) {

                    if (log.isDebugEnabled()) {
                        log.debug(String.format("Viglet Turing Index Request URI: %s", httpPost.getURI()));
                        log.debug(String.format("JSON: %s", jsonResult));
                        log.debug(
                                String.format("Viglet Turing indexer response HTTP result is: %s, for request uri: %s",
                                        response.getStatusLine().getStatusCode(), httpPost.getURI()));
                        log.debug(String.format("Viglet Turing indexer response HTTP result is: %s",
                                httpPost.getEntity().toString()));
                    }
                    turSNJobItems.getTuringDocuments().clear();
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
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

    public static String channelBreadcrumb(Channel channel) throws ApplicationException, ValidationException {
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

    public static String normalizeText(String text) {
        return text.replace("-", "–").replace(" ", "-").replace("\\?", "%3F");
    }

    public static Site getSite(ManagedObject mo, IHandlerConfiguration config) {
        List<Site> sites = new ArrayList<>();
        try {
            if (mo instanceof ContentInstance) {
                ContentInstance ci = (ContentInstance) mo;
                for (ChannelRef channelRef : ci.getChannelAssociations()) {
                    sites = getSitesFromChannel(channelRef.getChannel());

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
            if (selectedSite != null)
                return selectedSite;
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
        List<Site> sites = new ArrayList<Site>();
        if (channel != null) {
            for (SiteRef siteRef : channel.getSiteRefs()) {
                sites.add(siteRef.getSite());
            }
        }
        return sites;
    }

    public static String getSiteDomain(ManagedObject mo, IHandlerConfiguration config)
            throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
        String siteName = getSiteName(mo, config);
        return getSiteDomainBySiteName(siteName, config);

    }

    public static String getSiteDomainBySiteName(String siteName, IHandlerConfiguration config)
            throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
        if (log.isDebugEnabled()) {
            log.debug("ETLTuringTranslator getSiteUrl:" + siteName);
        }

        return config.getCDAURLPrefix(siteName);

    }

    public static String getSiteUrl(ManagedObject mo, IHandlerConfiguration config)
            throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
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

    private static String getSiteNameFromContentInstance(ManagedObject mo, IHandlerConfiguration config)
            throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
        return getSiteName(mo, config);
    }

    private static String createSiteURL(ManagedObject mo, IHandlerConfiguration config)
            throws ApplicationException, RemoteException, AuthorizationException, ValidationException {
        Site site = getSite(mo, config);
        String siteName = site.getName();
        if (log.isDebugEnabled()) {
            log.debug("ETLTuringTranslator getSiteUrl:" + siteName);
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
            url.append(SLASH);
            url.append(getLocale(mo, config));
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
            if (asLocale != null && asLocale.getLocale() != null && asLocale.getLocale().getJavaLocale() != null) {
                return asLocale.getLocale().getJavaLocale().toString();
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
        } catch (ApplicationException | RemoteException e) {
            log.error(e.getMessage(), e);
        }
        return asLocaleData;
    }

    public static String getSiteNameFromManagedObjectVCMRef(ManagedObjectVCMRef managedObjectVCMRef,
                                                            IHandlerConfiguration config) {
        String siteName = null;
        try {
            siteName = TuringUtils.getSiteName(managedObjectVCMRef.retrieveManagedObject(), config);
        } catch (ApplicationException | RemoteException e) {
            log.error(e.getMessage(), e);
        }
        return siteName;
    }
}
