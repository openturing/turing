package com.viglet.turing.connector.webcrawler;

import com.google.common.collect.Iterators;
import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.job.TurSNJobAction;
import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;
import com.viglet.turing.client.sn.job.TurSNJobUtils;
import com.viglet.turing.connector.webcrawler.ext.TurWCExtInterface;
import com.viglet.turing.connector.webcrawler.ext.TurWCExtLocaleInterface;
import generator.RandomUserAgentGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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

    public TurWCProcess(@Value("${turing.url}") String turingUrl,
                        @Value("${turing.apiKey}") String turingApiKey,
                        @Value("${turing.wc.timeout:5000}") int timeout,
                        @Value("${turing.turing.wc.job.size:50}") int jobSize,
                        @Value("${turing.wc.referrer:https://www.google.com}") String referrer) {

        this.turingUrl = turingUrl;
        this.turingApiKey = turingApiKey;
        this.timeout = timeout;
        this.jobSize = jobSize;
        this.referrer = referrer;
    }

    public void start(String website, String snSite, TurWCCustomDocument turWCCustomDocument,
                      List<String> allowUrls,
                      List<String> notAllowUrls,
                      List<String> notAllowExtensions) {
        this.notAllowExtensions.addAll(notAllowExtensions);
        this.notAllowUrls.addAll(notAllowUrls);
        this.website = website;
        this.snSite = snSite;
        log.info("User Agent: " + userAgent);
        allowUrls.forEach(url -> {
            remainingLinks.add(this.website + url);
            getPageLinks(turWCCustomDocument);
        });
        if (turSNJobItems.size() > 0) {
            sendToTuring();
            turSNJobItems = new TurSNJobItems();
            getInfoQueue();
        }
    }

    private void getInfoQueue() {
        log.info("Total Job Item: " + Iterators.size(turSNJobItems.iterator()));
        log.info("Total Visited Links: " + (long) visitedLinks.size());
        log.info("Queue Size: " + (long) remainingLinks.size());
    }

    public void getPageLinks(TurWCCustomDocument turWCCustomDocument) {
        while (!remainingLinks.isEmpty()) {
            String url = remainingLinks.poll();
            try {
                log.info(url);
                Document document = getHTML(url);
                addTurSNJobItems(getLocale(turWCCustomDocument, url, document),
                        getJobItemAttributes(turWCCustomDocument.getAttributes(), url, document));
                getInfoQueue();
                for (Element page : document.select(A_HREF)) {
                    String attr = page.attr(ABS_HREF);
                    final String pageUrl = getPageUrl(attr);
                    if (canBeIndexed(pageUrl)) {
                        if (visitedLinks.add(pageUrl) && !remainingLinks.offer(pageUrl)) {
                            log.error("Item didn't add to queue: " + pageUrl);
                        }
                    } else {
                        log.debug("Ignored: " + pageUrl);
                    }
                }
            } catch (IOException e) {
                log.error(e.getMessage());
            }
        }
    }

    private Map<String, Object> getJobItemAttributes(List<TurWCCustomClass> turWCCustomClasses,
                                                     String url, Document document) {
        Map<String, Object> turSNJobItemAttributes = new HashMap<>();
        turWCCustomClasses.forEach(turWCCustomClass ->
                Optional.ofNullable(turWCCustomClass.getText()).ifPresentOrElse(text ->
                                turSNJobItemAttributes.put(turWCCustomClass.getAttribute(), text)
                        , () -> {
                            try {
                                if (!StringUtils.isEmpty(turWCCustomClass.getClassName()))

                                    turSNJobItemAttributes.put(turWCCustomClass.getAttribute(),
                                            ((TurWCExtInterface) Class.forName(turWCCustomClass.getClassName())
                                                    .getDeclaredConstructor().newInstance())
                                                    .consume(getTurWCContext(url, document)));
                            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                                     NoSuchMethodException |
                                     ClassNotFoundException e) {
                                log.error(e.getMessage(), e);
                            }
                        }
                ));
        return turSNJobItemAttributes;
    }

    private void addTurSNJobItems(Locale locale, Map<String, Object> turSNJobItemAttributes) {
        turSNJobItems.add(new TurSNJobItem(TurSNJobAction.CREATE, locale,
                turSNJobItemAttributes));
        if (turSNJobItems.size() >= jobSize) {
            sendToTuring();
            turSNJobItems = new TurSNJobItems();
        }
    }

    private void sendToTuring() {
        if (log.isDebugEnabled()) {
            for (TurSNJobItem turSNJobItem : turSNJobItems) {
                log.debug("TurSNJobItem Id: " + turSNJobItem.getAttributes().get(ID_ATTR));
            }
        }
        try {
            TurSNJobUtils.importItems(turSNJobItems,
                    new TurSNServer(URI.create(turingUrl).toURL(), snSite,
                            turingApiKey),
                    false);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }

    }

    private Locale getLocale(TurWCCustomDocument turWCCustomDocument, String url, Document document) {

        return Optional.ofNullable(turWCCustomDocument.getLocale())
                .orElseGet(() -> {
                    if (!StringUtils.isEmpty(turWCCustomDocument.getLocaleClass())) {
                        try {
                            return ((TurWCExtLocaleInterface) Class.forName(turWCCustomDocument.getLocaleClass())
                                    .getDeclaredConstructor().newInstance())
                                    .consume(getTurWCContext(url, document));
                        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                                 NoSuchMethodException | ClassNotFoundException e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                    return Locale.US;
                });
    }

    private TurWCContext getTurWCContext(String url, Document document) {
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
            }
        }
        return pageUrlNormalized;
    }

    private static String removeLastChar(String pageUrl) {
        return pageUrl.substring(0, pageUrl.length() - 1);
    }

    private Document getHTML(String url) throws IOException {
        Document document = Jsoup.connect(url)
                .userAgent(userAgent)
                .referrer(referrer)
                .timeout(timeout)
                .get();
        document.outputSettings().escapeMode(Entities.EscapeMode.xhtml);
        document.outputSettings().charset(StandardCharsets.ISO_8859_1);
        document.charset(StandardCharsets.ISO_8859_1);
        return document;
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
