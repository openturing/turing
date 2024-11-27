package com.viglet.turing.connector.webcrawler;

import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.auth.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.commons.cache.TurCustomClassCache;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.webcrawler.commons.TurWCContext;
import com.viglet.turing.connector.webcrawler.commons.ext.TurWCExtInterface;
import com.viglet.turing.connector.webcrawler.commons.ext.TurWCExtLocaleInterface;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCAttributeMapping;
import com.viglet.turing.connector.webcrawler.persistence.model.TurWCSource;
import com.viglet.turing.connector.webcrawler.persistence.repository.TurWCAllowUrlRepository;
import com.viglet.turing.connector.webcrawler.persistence.repository.TurWCAttributeMappingRepository;
import com.viglet.turing.connector.webcrawler.persistence.repository.TurWCFileExtensionRepository;
import com.viglet.turing.connector.webcrawler.persistence.repository.TurWCNotAllowUrlRepository;
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
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class TurWCProcess {
    public static final String ID_ATTR = "id";
    public static final String MAILTO = "mailto";
    public static final String TEL = "tel:";
    public static final String JAVASCRIPT = "javascript:";
    public static final String A_HREF = "a[href]";
    public static final String ABS_HREF = "abs:href";
    private final String turingUrl;
    private final String turingApiKey;
    private final List<String> allowUrls = new ArrayList<>();
    private final List<String> notAllowUrls = new ArrayList<>();
    private final List<String> notAllowExtensions = new ArrayList<>();
    private TurSNJobItems turSNJobItems = new TurSNJobItems();
    private final String userAgent = RandomUserAgentGenerator.getNextNonMobile();
    private final Set<String> visitedLinks = new HashSet<>();
    private final Queue<String> remainingLinks = new LinkedList<>();
    private String website;
    private Collection<String> snSites;
    private final int timeout;
    private final int jobSize;
    private final String referrer;
    private String username;
    private String password;
    private final TurWCAllowUrlRepository turWCAllowUrlRepository;
    private final TurWCNotAllowUrlRepository turWCNotAllowUrlRepository;
    private final TurWCFileExtensionRepository turWCFileExtensionRepository;
    private final TurWCAttributeMappingRepository turWCAttributeMappingRepository;

    @Inject
    public TurWCProcess(@Value("${turing.url}") String turingUrl,
                        @Value("${turing.apiKey}") String turingApiKey,
                        @Value("${turing.wc.timeout:5000}") int timeout,
                        @Value("${turing.wc.job.size:50}") int jobSize,
                        @Value("${turing.wc.referrer:https://www.google.com}") String referrer,
                        TurWCAllowUrlRepository turWCAllowUrlRepository,
                        TurWCNotAllowUrlRepository turWCNotAllowUrlRepository,
                        TurWCFileExtensionRepository turWCFileExtensionRepository,
                        TurWCAttributeMappingRepository turWCAttributeMappingRepository) {
        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.timeout = timeout;
        this.jobSize = jobSize;
        this.referrer = referrer;
        this.turWCAllowUrlRepository = turWCAllowUrlRepository;
        this.turWCNotAllowUrlRepository = turWCNotAllowUrlRepository;
        this.turWCFileExtensionRepository = turWCFileExtensionRepository;
        this.turWCAttributeMappingRepository = turWCAttributeMappingRepository;
    }

    public void start(TurWCSource turWCSource) {
        reset();
        turWCFileExtensionRepository.findByTurWCSource(turWCSource).ifPresent(source -> source.forEach(turWCFileExtension ->
                this.notAllowExtensions.add(turWCFileExtension.getExtension())));
        turWCNotAllowUrlRepository.findByTurWCSource(turWCSource).ifPresent(source -> source.forEach(turWCNotAllowUrl ->
                this.notAllowUrls.add(turWCNotAllowUrl.getUrl())));
        turWCAllowUrlRepository.findByTurWCSource(turWCSource).ifPresent(source -> source.forEach(turWCAllowUrl ->
                this.allowUrls.add(turWCAllowUrl.getUrl())));

        this.website = turWCSource.getUrl();
        this.snSites = turWCSource.getTurSNSites();
        this.username = turWCSource.getUsername();
        this.password = turWCSource.getPassword();
        log.info("User Agent: {}", userAgent);
        allowUrls.forEach(url -> {
            remainingLinks.add(this.website + url);
            getPagesFromQueue(turWCSource);
        });
        if (turSNJobItems.size() > 0) {
            sendToTuring();
            getInfoQueue();
        }
    }

    private void reset() {
        turSNJobItems = new TurSNJobItems();
        visitedLinks.clear();
    }

    private void getInfoQueue() {
        log.info("Total Job Item: {}", Iterators.size(turSNJobItems.iterator()));
        log.info("Total Visited Links: {}", (long) visitedLinks.size());
        log.info("Queue Size: {}", (long) remainingLinks.size());
    }

    public void getPagesFromQueue(TurWCSource turWCSource) {
        while (!remainingLinks.isEmpty()) {
            String url = remainingLinks.poll();
            getPage(turWCSource, url);
            sendToTuringWhenMaxSize();
            getInfoQueue();
        }
    }

    public TurSNJobItem getPage(TurWCSource turWCSource, String url) {
        try {
            log.info("{}: {}", url, turWCSource.getTurSNSites());
            Document document = getHTML(url);
            getPageLinks(document);
            return addTurSNJobItems(turWCSource, document, url);
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        return new TurSNJobItem();
    }

    private TurSNJobItem addTurSNJobItems(TurWCSource turWCSource, Document document, String url) {
        TurSNJobItem turSNJobItem = new TurSNJobItem(TurSNJobAction.CREATE, new ArrayList<>(snSites),
                getLocale(turWCSource, document, url),
                getJobItemAttributes(turWCSource, document, url));
        turSNJobItems.add(turSNJobItem);
        return turSNJobItem;
    }

    private void getPageLinks(Document document) {
        document.select(A_HREF).forEach(page -> addPageToQueue(getPageUrl(page.attr(ABS_HREF))));
    }

    private void addPageToQueue(String pageUrl) {
        if (canBeIndexed(pageUrl)) {
            if (visitedLinks.add(pageUrl) && !remainingLinks.offer(pageUrl)) {
                log.error("Item didn't add to queue: {}", pageUrl);
            }
        } else {
            log.debug("Ignored: {}", pageUrl);
        }
    }

    private void sendToTuringWhenMaxSize() {
        if (turSNJobItems.size() >= jobSize) {
            sendToTuring();
            turSNJobItems = new TurSNJobItems();
        }
    }

    public Map<String, Object> getJobItemAttributes(TurWCSource turWCSource, Document document, String url) {
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


    private void sendToTuring() {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        try {
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(), null,
                            new TurApiKeyCredentials(turingApiKey)),
                    false);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }

    public Locale getLocale(TurWCSource turWCSource, Document document, String url) {

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

    public TurWCContext getTurWCContext(Document document, String url) {
        return TurWCContext.builder()
                .document(document)
                .url(url)
                .timeout(timeout)
                .userAgent(userAgent)
                .referrer(referrer)
                .build();
    }

    private boolean canBeIndexed(String pageUrl) {
        return !isSharpUrl(pageUrl) && !isPagination(pageUrl) && !isJavascriptUrl(pageUrl)
                && pageUrl.startsWith(this.website)
                && StringUtils.startsWithAny(pageUrl,
                allowUrls.toArray(new String[0]))
                && !StringUtils.startsWithAny(getRelativePageUrl(pageUrl),
                notAllowUrls.toArray(new String[0]))
                && !StringUtils.endsWithAny(pageUrl,
                notAllowExtensions.toArray(new String[0]))
                && !StringUtils.equalsAny(pageUrl,
                visitedLinks.toArray(new String[0]));
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
            connection.header("Authorization", "Basic " + getBasicAuth());
        }
        Document document = connection.get();

        document.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        document.outputSettings().charset(StandardCharsets.ISO_8859_1);
        document.charset(StandardCharsets.ISO_8859_1);
        return document;
    }

    private String getBasicAuth() {
        String authString = this.username + ":" + this.password;
        return Base64.getEncoder().encodeToString(authString.getBytes());
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
