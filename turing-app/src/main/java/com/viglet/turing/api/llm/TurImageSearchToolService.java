package com.viglet.turing.api.llm;

import java.net.URI;
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

    @Tool(name = "search_images", description = """
            Searches for images on the web and returns image URLs with descriptions.
            Use this tool when the user asks to see, show, or find a photo, picture, or image of something.
            Examples: 'show me a photo of the Eiffel Tower', 'find an image of a Nintendo NES',
            'what does a capybara look like?', 'picture of the Mars rover'.
            Args:
                query (str): Required. What to search for (e.g., 'Nintendo Entertainment System console',
                    'Eiffel Tower at night', 'capybara animal').
                count (int): Optional. Number of images to return (1-8). Defaults to 3.""")
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
            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String href = link.absUrl("href");
                if (isImageUrl(href) && results.size() < maxResults) {
                    results.add(new ImageResult(link.text().strip(), href, null));
                }
            }

            // Also try img tags with src
            if (results.isEmpty()) {
                Elements imgs = doc.select("img[src]");
                for (Element img : imgs) {
                    String src = img.absUrl("src");
                    if (isImageUrl(src) && !src.contains("logo") && !src.contains("icon")
                            && results.size() < maxResults) {
                        String alt = img.attr("alt");
                        results.add(new ImageResult(alt.isBlank() ? query : alt, src, null));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[ImageSearch Tool] DuckDuckGo search failed: {}", e.getMessage());
        }
        return results;
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
            for (Element img : imgElements) {
                String src = img.hasAttr("data-src") ? img.attr("data-src") : img.absUrl("src");
                if (src.isEmpty()) src = img.absUrl("src");

                if (!src.isBlank() && src.startsWith("http") && results.size() < maxResults) {
                    String alt = img.attr("alt");
                    results.add(new ImageResult(alt.isBlank() ? query : alt, src, null));
                }
            }

            // Fallback: any image link with image extensions
            if (results.isEmpty()) {
                Elements links = doc.select("a[href]");
                for (Element link : links) {
                    String href = link.absUrl("href");
                    if (isImageUrl(href) && results.size() < maxResults) {
                        results.add(new ImageResult(link.text().strip(), href, null));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("[ImageSearch Tool] Bing search failed: {}", e.getMessage());
        }
        return results;
    }

    private boolean isImageUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg")
                || lower.contains("/image") || lower.contains("img=");
    }

    private record ImageResult(String title, String imageUrl, String sourceUrl) {}
}
