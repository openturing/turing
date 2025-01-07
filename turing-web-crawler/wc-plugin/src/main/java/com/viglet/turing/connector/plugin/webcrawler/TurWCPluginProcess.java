package com.viglet.turing.connector.plugin.webcrawler;

import com.google.inject.Inject;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.connector.commons.plugin.TurConnectorContext;
import com.viglet.turing.connector.commons.plugin.TurConnectorSource;
import com.viglet.turing.connector.plugin.webcrawler.persistence.repository.*;
import com.viglet.turing.connector.webcrawler.commons.TurWCContext;
import com.viglet.turing.connector.webcrawler.commons.ext.TurWCExtInterface;
import com.viglet.turing.connector.webcrawler.commons.ext.TurWCExtLocaleInterface;
import com.viglet.turing.connector.plugin.webcrawler.persistence.model.TurWCAttributeMapping;
import com.viglet.turing.connector.plugin.webcrawler.persistence.model.TurWCSource;
import generator.RandomUserAgentGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

@Slf4j
@Component
public class TurWCPluginProcess {
    public static final String MAILTO = "mailto";
    public static final String TEL = "tel:";
    public static final String JAVASCRIPT = "javascript:";
    public static final String A_HREF = "a[href]";
    public static final String ABS_HREF = "abs:href";
    public static final String WILD_CARD = "*";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BASIC = "Basic";
    public static final String WEB_CRAWLER = "WEB-CRAWLER";
    private final List<String> startingPoints = new ArrayList<>();
    private final List<String> allowUrls = new ArrayList<>();
    private final List<String> allowStartsWithUrls = new ArrayList<>();
    private final List<String> notAllowUrls = new ArrayList<>();
    private final List<String> notAllowStartsWithUrls = new ArrayList<>();
    private final List<String> notAllowExtensions = new ArrayList<>();
    private final TurWCStartingPointRepository turWCStartingPointsRepository;
    private final String userAgent = RandomUserAgentGenerator.getNextNonMobile();
    private final Set<String> visitedLinks = new HashSet<>();
    private final Set<String> indexedLinks = new HashSet<>();
    private final Queue<String> queueLinks = new LinkedList<>();
    private String website;
    private Collection<String> snSites;
    private final int timeout;
    private final String referrer;
    private String username;
    private String password;
    private final TurWCAllowUrlRepository turWCAllowUrlRepository;
    private final TurWCNotAllowUrlRepository turWCNotAllowUrlRepository;
    private final TurWCFileExtensionRepository turWCFileExtensionRepository;
    private final TurWCAttributeMappingRepository turWCAttributeMappingRepository;
    private TurConnectorContext turConnectorContext;

    @Inject
    public TurWCPluginProcess(@Value("${turing.wc.timeout:5000}") int timeout,
                              @Value("${turing.wc.referrer:https://www.google.com}") String referrer,
                              TurWCAllowUrlRepository turWCAllowUrlRepository,
                              TurWCNotAllowUrlRepository turWCNotAllowUrlRepository,
                              TurWCFileExtensionRepository turWCFileExtensionRepository,
                              TurWCAttributeMappingRepository turWCAttributeMappingRepository,
                              TurWCStartingPointRepository turWCStartingPointsRepository) {
        this.timeout = timeout;
        this.referrer = referrer;
        this.turWCAllowUrlRepository = turWCAllowUrlRepository;
        this.turWCNotAllowUrlRepository = turWCNotAllowUrlRepository;
        this.turWCFileExtensionRepository = turWCFileExtensionRepository;
        this.turWCAttributeMappingRepository = turWCAttributeMappingRepository;
        this.turWCStartingPointsRepository = turWCStartingPointsRepository;
    }

    public void start(TurWCSource turWCSource, TurConnectorContext turConnectorContext) {
        this.turConnectorContext = turConnectorContext;
        this.turConnectorContext.startIndexing(new TurConnectorSource(turWCSource.getId(), turWCSource.getTurSNSites(),
                WEB_CRAWLER, turWCSource.getLocale()));
        turWCFileExtensionRepository.findByTurWCSource(turWCSource).ifPresent(source ->
                source.forEach(turWCFileExtension ->
                        this.notAllowExtensions.add(turWCFileExtension.getExtension())));
        turWCNotAllowUrlRepository.findByTurWCSource(turWCSource).ifPresent(source ->
                source.forEach(turWCNotAllowUrl -> {
                            if (turWCNotAllowUrl.getUrl().trim().endsWith(WILD_CARD)) {
                                this.notAllowStartsWithUrls.add(StringUtils.chop(turWCNotAllowUrl.getUrl()));
                            } else {
                                this.notAllowUrls.add(turWCNotAllowUrl.getUrl());
                            }
                        }
                ));
        turWCAllowUrlRepository.findByTurWCSource(turWCSource).ifPresent(source ->
                source.forEach(turWCAllowUrl -> {
                            if (turWCAllowUrl.getUrl().trim().endsWith(WILD_CARD)) {
                                this.allowStartsWithUrls.add(StringUtils.chop(turWCAllowUrl.getUrl().trim()));
                            } else {
                                this.allowUrls.add(turWCAllowUrl.getUrl());
                            }
                        }
                ));
        turWCStartingPointsRepository.findByTurWCSource(turWCSource).ifPresent(source ->
                source.forEach(turWCStartingPoint ->
                        this.startingPoints.add(turWCStartingPoint.getUrl())
                ));
        this.website = turWCSource.getUrl();
        this.snSites = turWCSource.getTurSNSites();
        this.username = turWCSource.getUsername();
        this.password = turWCSource.getPassword();
        log.info("User Agent: {}", userAgent);
        startingPoints.forEach(url -> {
            queueLinks.offer(this.website + url);
            getPagesFromQueue(turWCSource);
        });
        finished(turConnectorContext);
    }

    private static void finished(TurConnectorContext turConnectorContext) {
        turConnectorContext.finishIndexing();
    }


    private void getPagesFromQueue(TurWCSource turWCSource) {
        while (!queueLinks.isEmpty()) {
            String url = queueLinks.poll();
            getPage(turWCSource, url);
        }
    }

    private void getPage(TurWCSource turWCSource, String url) {
        try {
            log.info("{}: {}", url, turWCSource.getTurSNSites());
            Document document = getHTML(url);
            String checksum = getCRC32Checksum(document.html().getBytes());
            getPageLinks(document);
            String pageUrl = getPageUrl(url);
            if (canBeIndexed(pageUrl)) {
                indexedLinks.add(pageUrl);
                log.info("WC is creating a Job Item: {}", url);
                addTurSNJobItem(turWCSource, document, url, checksum);
                return;
            } else {
                log.debug("Ignored: {}", url);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        new TurSNJobItem();
    }

    private void getPageLinks(Document document) {
        document.select(A_HREF).forEach(page -> addPageToQueue(getPageUrl(page.attr(ABS_HREF))));
    }

    private void addPageToQueue(String pageUrl) {
        if (canBeAddToQueue(pageUrl) && visitedLinks.add(pageUrl) && !queueLinks.offer(pageUrl)) {
            log.error("Item didn't add to queue: {}", pageUrl);
        }
    }

    private boolean isValidToAddQueue(String pageUrl) {
        return isNotMailUrl(pageUrl)
                && isNotTelUrl(pageUrl)
                && !StringUtils.equalsAny(pageUrl, queueLinks.toArray(new String[0]))
                && !isSharpUrl(pageUrl) && !isPagination(pageUrl) && !isJavascriptUrl(pageUrl)
                && pageUrl.startsWith(this.website)
                && (
                StringUtils.startsWithAny(getRelativePageUrl(pageUrl), allowStartsWithUrls.toArray(new String[0]))
                        || StringUtils.equalsAny(getRelativePageUrl(pageUrl), allowUrls.toArray(new String[0]))
        )
                && !StringUtils.startsWithAny(getRelativePageUrl(pageUrl), notAllowStartsWithUrls.toArray(new String[0]))
                && !StringUtils.equalsAny(getRelativePageUrl(pageUrl), notAllowUrls.toArray(new String[0]))
                && !StringUtils.endsWithAny(pageUrl, notAllowExtensions.toArray(new String[0]));
    }

    private void addTurSNJobItem(TurWCSource turWCSource, Document document, String url, String checksum) {
        turConnectorContext.addJobItem(new TurSNJobItem(TurSNJobAction.CREATE, new ArrayList<>(snSites),
                getLocale(turWCSource, document, url),
                getJobItemAttributes(turWCSource, document, url), null, checksum));
    }

    public static String getCRC32Checksum(byte[] bytes) {
        Checksum crc32 = new CRC32();
        crc32.update(bytes, 0, bytes.length);
        return String.valueOf(crc32.getValue());
    }

    private Map<String, Object> getJobItemAttributes(TurWCSource turWCSource, Document document, String url) {
        Map<String, Object> turSNJobItemAttributes = new HashMap<>();
        turWCAttributeMappingRepository.findByTurWCSource(turWCSource).ifPresent(source ->
                source.forEach(turWCCustomClass ->
                        Optional.ofNullable(turWCCustomClass.getText()).ifPresentOrElse(text ->
                                        usesText(turWCCustomClass, text, turSNJobItemAttributes)
                                , () -> {
                                    if (!StringUtils.isEmpty(turWCCustomClass.getClassName()))
                                        usesCustomClass(document, url, turWCCustomClass, turSNJobItemAttributes);
                                }
                        )));
        return turSNJobItemAttributes;
    }

    private void usesCustomClass(Document document, String url, TurWCAttributeMapping turWCCustomClass,
                                 Map<String, Object> turSNJobItemAttributes) {
        getCustomClass(document, url, turWCCustomClass)
                .ifPresent(turMultiValue -> turMultiValue.forEach(attributeValue -> {
                    if (!StringUtils.isBlank(attributeValue)) {
                        if (turSNJobItemAttributes.containsKey(turWCCustomClass.getName())) {
                            addItemInExistingAttribute(attributeValue,
                                    turSNJobItemAttributes, turWCCustomClass.getName());
                        } else {
                            addFirstItemToAttribute(turWCCustomClass.getName(),
                                    attributeValue, turSNJobItemAttributes);
                        }
                    }
                }));
    }

    private static void usesText(TurWCAttributeMapping turWCCustomClass, String text,
                                 Map<String, Object> turSNJobItemAttributes) {
        turSNJobItemAttributes.put(turWCCustomClass.getName(), text);
    }

    private Optional<TurMultiValue> getCustomClass(Document document, String url,
                                                   TurWCAttributeMapping turWCAttributeMapping) {
        return TurCustomClassCache.getCustomClassMap(turWCAttributeMapping.getClassName())
                .flatMap(classInstance -> ((TurWCExtInterface) classInstance)
                        .consume(getTurWCContext(document, url)));
    }

    private static void addItemInExistingAttribute(String attributeValue,
                                                   Map<String, Object> attributes,
                                                   String attributeName) {
        if (attributes.get(attributeName) instanceof ArrayList)
            addItemToArray(attributes, attributeName, attributeValue);
        else convertAttributeSingleValueToArray(attributes, attributeName, attributeValue);
    }

    private static void convertAttributeSingleValueToArray(Map<String, Object> attributes,
                                                           String attributeName, String attributeValue) {
        List<Object> attributeValues = new ArrayList<>();
        attributeValues.add(attributes.get(attributeName));
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    private static void addItemToArray(Map<String, Object> attributes, String attributeName, String attributeValue) {
        List<String> attributeValues = new ArrayList<>(((List<?>) attributes.get(attributeName))
                .stream().map(String.class::cast).toList());
        attributeValues.add(attributeValue);
        attributes.put(attributeName, attributeValues);
    }

    private void addFirstItemToAttribute(String attributeName,
                                         String attributeValue,
                                         Map<String, Object> attributes) {
        attributes.put(attributeName, attributeValue);
    }

    private Locale getLocale(TurWCSource turWCSource, Document document, String url) {
        return Optional.ofNullable(turWCSource.getLocale())
                .orElseGet(() -> {
                    if (!StringUtils.isEmpty(turWCSource.getLocaleClass())) {
                        return TurCustomClassCache.getCustomClassMap(turWCSource.getLocaleClass())
                                .map(classInstance -> ((TurWCExtLocaleInterface) classInstance)
                                        .consume(getTurWCContext(document, url)))
                                .orElse(Locale.US);
                    }
                    return Locale.US;
                });
    }

    private TurWCContext getTurWCContext(Document document, String url) {
        return TurWCContext.builder()
                .document(document)
                .url(url)
                .timeout(timeout)
                .userAgent(userAgent)
                .referrer(referrer)
                .build();
    }

    private boolean canBeIndexed(String pageUrl) {
        return isValidToAddQueue(pageUrl)
                && !StringUtils.equalsAny(pageUrl, indexedLinks.toArray(new String[0]));
    }

    private boolean canBeAddToQueue(String pageUrl) {
        return isValidToAddQueue(pageUrl)
                && !StringUtils.equalsAny(pageUrl, visitedLinks.toArray(new String[0]));
    }

    private static boolean isJavascriptUrl(String pageUrl) {
        return pageUrl.contains(JAVASCRIPT);
    }

    private String getPageUrl(String attr) {
        String pageUrl = getUrlWithoutParameters(!isHttpUrl(attr)
                && isNotMailUrl(attr) && isNotTelUrl(attr) ? this.website + attr : attr);
        String pageUrlNormalized = pageUrl.endsWith("/") ? removeLastChar(pageUrl) : pageUrl;
        if (isNotMailUrl(attr) && isNotTelUrl(attr)) {
            try {
                return URI.create(pageUrlNormalized).normalize().toString();
            } catch (IllegalArgumentException ignored) {
                // No error
            }
        }
        return pageUrlNormalized;
    }

    private static String removeLastChar(String pageUrl) {
        return pageUrl.substring(0, pageUrl.length() - 1);
    }

    private Document getHTML(String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(userAgent)
                .referrer(referrer)
                .timeout(timeout);
        if (isBasicAuth()) {
            connection.header(AUTHORIZATION, "%s %s".formatted(BASIC, getBasicAuth()));
        }
        Document document = connection.get();

        document.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        document.outputSettings().charset(StandardCharsets.ISO_8859_1);
        document.charset(StandardCharsets.ISO_8859_1);
        return document;
    }

    private String getBasicAuth() {
        return Base64.getEncoder().encodeToString("%s:%s".formatted(this.username, this.password).getBytes());
    }

    private boolean isBasicAuth() {
        return this.username != null;
    }

    private String getRelativePageUrl(String pageUrl) {
        return pageUrl.replaceAll(this.website, "");
    }

    private static boolean isPagination(String pageUrl) {
        return pageUrl.contains("/page/");
    }

    private static boolean isSharpUrl(String attr) {
        return attr.contains("#");
    }

    private static boolean isHttpUrl(String attr) {
        return attr.toLowerCase().startsWith("http");
    }

    private static boolean isNotMailUrl(String attr) {
        return !attr.toLowerCase().startsWith(MAILTO);
    }

    private static boolean isNotTelUrl(String attr) {
        return !attr.toLowerCase().startsWith(TEL);
    }

    private String getUrlWithoutParameters(String url) {
        try {
            URI uri = new URI(url);
            return new URI(uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    null,
                    uri.getFragment()).toString();
        } catch (URISyntaxException e) {
            return url;
        }
    }
}