package com.viglet.turing.genai.tool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurImageSearchToolService {

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_RESULTS = 8;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Tool(name = "search_images", description = ".")
    public String searchImages(String query, Integer count) {
        log.info("[ImageSearch Tool] search_images called: query='{}', count={}", query, count);
        int maxResults = (count != null && count > 0 && count <= MAX_RESULTS) ? count : 3;

        try {
            List<ImageResult> results = searchDuckDuckGo(query, maxResults);

            if (results.isEmpty()) {
                results = searchBing(query, maxResults);
            }

            if (results.isEmpty()) {
                return "No images found for '" + query + "'. Try a more specific search term.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Images found for '").append(query).append("':\n\n");
            for (int i = 0; i < results.size(); i++) {
                ImageResult img = results.get(i);
                sb.append(i + 1).append(". ");
                if (img.title != null && !img.title.isBlank()) {
                    sb.append(img.title).append("\n");
                }
                sb.append("   Image: ").append(img.imageUrl).append("\n");
                if (img.sourceUrl != null && !img.sourceUrl.isBlank()) {
                    sb.append("   Source: ").append(img.sourceUrl).append("\n");
                }
                sb.append("\n");
            }
            sb.append("TIP: You can display these images using markdown: ![description](image_url)");

            String result = sb.toString();
            log.info("[ImageSearch Tool] search_images: found {} images for '{}'", results.size(), query);
            return result;
        } catch (Exception e) {
            log.error("[ImageSearch Tool] search_images failed for '{}': {}", query, e.getMessage());
            return "Error searching for images: " + e.getMessage()
                    + ". Try a different search term.";
        }
    }

    private List<ImageResult> searchDuckDuckGo(String query, int maxResults) {
        List<ImageResult> results = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://lite.duckduckgo.com/lite/?q=" + encoded + "&kp=-1&iax=images&ia=images";

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            // DuckDuckGo lite: extract links with image extensions
            Elements elements = doc.select("a[href]");
            extractFromLinks(results, elements, maxResults);

            // Also try img tags with src
            if (results.isEmpty()) {
                Elements imgs = doc.select("img[src]");
                extractFromImages(results, imgs, query, maxResults);
            }
        } catch (Exception e) {
            log.debug("[ImageSearch Tool] DuckDuckGo search failed: {}", e.getMessage());
        }
        return results;
    }

    private void extractFromLinks(List<ImageResult> results, Elements links, int maxResults) {
        for (Element link : links) {
            if (results.size() >= maxResults) {
                break;
            }
            String href = link.absUrl("href");
            if (isImageUrl(href)) {
                results.add(new ImageResult(link.text().strip(), href, null));
            }
        }
    }

    private void extractFromImages(List<ImageResult> results, Elements imgs, String query, int maxResults) {
        for (Element img : imgs) {
            if (results.size() >= maxResults) {
                break;
            }
            String src = img.absUrl("src");
            if (isImageUrl(src) && !src.contains("logo") && !src.contains("icon")) {
                String alt = img.attr("alt");
                results.add(new ImageResult(alt.isBlank() ? query : alt, src, null));
            }
        }
    }

    private List<ImageResult> searchBing(String query, int maxResults) {
        List<ImageResult> results = new ArrayList<>();
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.bing.com/images/search?q=" + encoded + "&form=HDRSC2&first=1";

            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .referrer("https://www.bing.com/")
                    .get();

            // Bing image results: look for img tags within result containers
            Elements imgElements = doc.select("img.mimg, img[data-src], a.iusc img");
            extractBingImages(results, imgElements, query, maxResults);

            // Fallback: any image link with image extensions
            if (results.isEmpty()) {
                Elements links = doc.select("a[href]");
                extractFromLinks(results, links, maxResults);
            }
        } catch (Exception e) {
            log.debug("[ImageSearch Tool] Bing search failed: {}", e.getMessage());
        }
        return results;
    }

    private void extractBingImages(List<ImageResult> results, Elements imgElements, String query, int maxResults) {
        for (Element img : imgElements) {
            if (results.size() >= maxResults) {
                break;
            }
            String src = img.hasAttr("data-src") ? img.attr("data-src") : img.absUrl("src");
            if (src.isEmpty()) {
                src = img.absUrl("src");
            }

            if (!src.isBlank() && src.startsWith("http")) {
                String alt = img.attr("alt");
                results.add(new ImageResult(alt.isBlank() ? query : alt, src, null));
            }
        }
    }

    private boolean isImageUrl(String url) {
        if (url == null || url.isBlank())
            return false;
        String lower = url.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")
                || lower.contains("/image") || lower.contains("img=");
    }

    private record ImageResult(String title, String imageUrl, String sourceUrl) {
    }
}
