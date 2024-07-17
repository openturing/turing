package com.viglet.turing.connector.webcrawler;

import com.google.common.collect.Iterators;
import com.google.inject.Inject;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.credentials.TurApiKeyCredentials;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.connector.webcrawler.commons.TurWCContext;
import com.viglet.turing.connector.webcrawler.commons.ext.TurWCExtInterface;
import com.viglet.turing.connector.webcrawler.commons.ext.TurWCExtLocaleInterface;
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
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
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
    private final List<String> notAllowUrls = new ArrayList<>();
    private final List<String> notAllowExtensions = new ArrayList<>();
    private TurSNJobItems turSNJobItems = new TurSNJobItems();
    private final String userAgent = RandomUserAgentGenerator.getNextNonMobile();
    private final Set<String> visitedLinks = new HashSet<>();
    private final Queue<String> remainingLinks = new LinkedList<>();
    private String website;
    private String snSite;
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

        this.website = turWCSource.getUrl();
        this.snSite = turWCSource.getTurSNSite();
        this.username = turWCSource.getUsername();
        this.password = turWCSource.getPassword();
        log.info("User Agent: {}", userAgent);
        turWCAllowUrlRepository
                .findByTurWCSource(turWCSource).ifPresent(source ->
                source.forEach(turWCAllowUrl -> {
                    remainingLinks.add(this.website + turWCAllowUrl.getUrl());
                    getPageLinks(turWCSource);
                }));
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

    public void getPageLinks(TurWCSource turWCSource) {
        while (!remainingLinks.isEmpty()) {
            String url = remainingLinks.poll();
            try {
                log.info("{}: {}", turWCSource.getTurSNSite(), url);
                Document document = getHTML(url);
                addTurSNJobItems(getLocale(turWCSource, document, url),
                        getJobItemAttributes(turWCSource, document, url));
                sendToTuringWhenMaxSize();
                getInfoQueue();
                for (Element page : document.select(A_HREF)) {
                    String attr = page.attr(ABS_HREF);
                    final String pageUrl = getPageUrl(attr);
                    if (canBeIndexed(pageUrl)) {
                        if (visitedLinks.add(pageUrl) && !remainingLinks.offer(pageUrl)) {
                            log.error("Item didn't add to queue: {}", pageUrl);
                        }
                    } else {
                        log.debug("Ignored: {}", pageUrl);
                    }
                }
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
    }

    private void sendToTuringWhenMaxSize() {
        if (turSNJobItems.size() >= jobSize) {
            sendToTuring();
            turSNJobItems = new TurSNJobItems();
        }
    }

    private Map<String, Object> getJobItemAttributes(TurWCSource turWCSource, Document document, String url) {
        Map<String, Object> turSNJobItemAttributes = new HashMap<>();
        turWCAttributeMappingRepository.findByTurWCSource(turWCSource).ifPresent(source -> source.forEach(turWCCustomClass ->
                Optional.ofNullable(turWCCustomClass.getText()).ifPresentOrElse(text ->
                                turSNJobItemAttributes.put(turWCCustomClass.getName(), text)
                        , () -> {
                            try {
                                if (!StringUtils.isEmpty(turWCCustomClass.getClassName()))
                                    ((TurWCExtInterface) Class.forName(turWCCustomClass.getClassName())
                                            .getDeclaredConstructor().newInstance())
                                            .consume(getTurWCContext(document, url))
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
                            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                                     NoSuchMethodException |
                                     ClassNotFoundException e) {
                                log.error(e.getMessage(), e);
                            }
                        }
                )));
        return turSNJobItemAttributes;
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


    private void addTurSNJobItems(Locale locale, Map<String, Object> turSNJobItemAttributes) {
        turSNJobItems.add(new TurSNJobItem(TurSNJobAction.CREATE, Collections.singletonList(snSite), locale,
                turSNJobItemAttributes));
    }

    private void sendToTuring() {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.debug("TurSNJobItem Id: {}", turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        try {
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(),null,
                            new TurApiKeyCredentials(turingApiKey)),
                    false);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }

    private Locale getLocale(TurWCSource turWCSource, Document document, String url) {

        return Optional.ofNullable(turWCSource.getLocale())
                .orElseGet(() -> {
                    if (!StringUtils.isEmpty(turWCSource.getLocaleClass())) {
                        try {
                            return ((TurWCExtLocaleInterface) Class.forName(turWCSource.getLocaleClass())
                                    .getDeclaredConstructor().newInstance())
                                    .consume(getTurWCContext(document, url));
                        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                                 NoSuchMethodException | ClassNotFoundException e) {
                            log.error(e.getMessage(), e);
                        }
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
        return !isSharpUrl(pageUrl) && !isPagination(pageUrl) && !isJavascriptUrl(pageUrl)
                && pageUrl.startsWith(this.website)
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
