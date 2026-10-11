/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.turing.client.sn.inventory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import com.viglet.turing.client.sn.TurSNTimeouts;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;

import com.viglet.turing.client.sn.TurSNServer;
import com.viglet.turing.client.sn.utils.TurSNClientUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * T1035 / §XX.20 — reads what the Semantic Navigation index holds for a source,
 * so a connector's content audit gains the comparison it cannot make for itself.
 *
 * <p>A connector already audits its source against its own indexing ledger, so
 * it knows what it published and what it recorded. What neither side knows is
 * what survived in the index: a write that failed or a delete that never arrived
 * leaves the ledger saying processed and the index saying otherwise. T1029 built
 * the endpoint; this is the client, so the audit is a call rather than an
 * integration.
 *
 * <p>The cursor loop is the client's job, not the caller's. Iterating
 * {@link #entries} walks every page of a locale, and {@link #allLocales} walks
 * every locale of the site:
 *
 * <pre>{@code
 * TurSNInventoryClient inventory = new TurSNInventoryClient(server);
 * for (TurSNInventoryEntry indexed : inventory.allLocales("aem")) {
 *     MyDoc source = mySource.find(indexed.id());
 *     if (source == null) {
 *         report.inIndexNotInSource(indexed.id());
 *     } else if (indexed.checksumDiffers(source.checksum())) {
 *         report.differentVersions(indexed.id());
 *     }
 * }
 * // whatever the source has that the sweep never yielded is the third difference
 * }</pre>
 *
 * <p>A page whose {@code supported} is false throws rather than iterating empty.
 * That is the whole point: an audit that reads "cannot answer" as "the index
 * holds nothing" concludes every document is missing and republishes the source.
 *
 * <p>Authentication rides on the same {@link TurSNServer} credentials the
 * indexing path uses. The endpoint is not public, so an index-only credential
 * may not carry the authority to read it — that arrives as a 401/403 and is
 * thrown, not swallowed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
public class TurSNInventoryClient {

    /** Page size when the caller names none — the server's own default. */
    public static final int DEFAULT_ROWS = 200;

    private static final String INVENTORY_CONTEXT = "%s/api/sn/%s/inventory";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build();

    private final TurSNServer turSNServer;
    private final String siteNameOrId;
    private final int rows;

    /** Audits the site the server is configured for, at the default page size. */
    public TurSNInventoryClient(TurSNServer turSNServer) {
        this(turSNServer, turSNServer.getSiteName(), DEFAULT_ROWS);
    }

    /**
     * @param siteNameOrId the site to audit — its name (what a connector holds)
     *                     or its id; the server accepts either
     * @param rows         page size; the server clamps it to its own ceiling
     */
    public TurSNInventoryClient(TurSNServer turSNServer, String siteNameOrId, int rows) {
        this.turSNServer = turSNServer;
        this.siteNameOrId = siteNameOrId;
        this.rows = rows;
    }

    /**
     * Reads one page. Reach for this only to checkpoint a sweep across runs —
     * store the {@code nextCursor} and resume from it; otherwise iterate
     * {@link #entries}.
     *
     * @param source the {@code source_apps} value to filter by, or {@code null}
     *               for every document
     * @param locale the language tag to enumerate, or {@code null} for the site's
     *               first locale by position
     * @param after  the previous page's cursor, or {@code null} to start
     * @throws TurSNInventoryException on any non-200, and when the engine reports
     *                                 it cannot enumerate its index
     */
    public TurSNInventoryPage page(String source, String locale, String after) {
        String url = INVENTORY_CONTEXT.formatted(turSNServer.getServerURL(), siteNameOrId);
        try (CloseableHttpClient client = TurSNTimeouts.httpClientFor(turSNServer)) {
            URIBuilder uri = new URIBuilder(url).addParameter("rows", Integer.toString(rows));
            addIfPresent(uri, "source", source);
            addIfPresent(uri, "locale", locale);
            addIfPresent(uri, "after", after);
            HttpGet httpGet = new HttpGet(uri.build());
            httpGet.setHeader("Accept", "application/json");
            TurSNClientUtils.authentication(httpGet, turSNServer.getCredentials(),
                    turSNServer.getApiKey());
            Response response = client.execute(httpGet,
                    r -> new Response(r.getCode(), readBody(r)));
            if (response.status() != 200) {
                throw new TurSNInventoryException("Inventory GET %s returned %d: %s"
                        .formatted(url, response.status(), errorMessage(response.body())),
                        response.status());
            }
            return assertSupported(read(response.body(), url), url);
        } catch (IOException | URISyntaxException e) {
            throw new TurSNInventoryException("Could not reach the inventory endpoint at " + url, e);
        }
    }

    /**
     * Every row the index holds for {@code source} in one locale, walking the
     * cursor as the iteration advances. Single-pass; a page is fetched only when
     * the previous one is exhausted.
     *
     * @param locale the language tag, or {@code null} for the site's first
     */
    public Iterable<TurSNInventoryEntry> entries(String source, String locale) {
        return () -> new PageWalker(source, locale);
    }

    /**
     * Every row the index holds for {@code source} across <em>every</em> locale
     * the site has. This is the full-source sweep an audit wants: the first page
     * reports the site's locales, and the walk continues into each in turn.
     */
    public Iterable<TurSNInventoryEntry> allLocales(String source) {
        return () -> {
            List<String> locales = page(source, null, null).locales();
            if (locales == null || locales.isEmpty()) {
                return new PageWalker(source, null);
            }
            return new LocaleWalker(source, locales);
        };
    }

    /** Walks the cursor within one locale. */
    private class PageWalker implements Iterator<TurSNInventoryEntry> {
        private final String source;
        private final String locale;
        private Iterator<TurSNInventoryEntry> current = java.util.Collections.emptyIterator();
        private String cursor;
        private boolean exhausted;

        PageWalker(String source, String locale) {
            this.source = source;
            this.locale = locale;
        }

        @Override
        public boolean hasNext() {
            while (!current.hasNext() && !exhausted) {
                TurSNInventoryPage next = page(source, locale, cursor);
                current = next.entries() == null
                        ? java.util.Collections.emptyIterator()
                        : next.entries().iterator();
                cursor = next.nextCursor();
                // A page that reports no more, or that could not advance the
                // cursor, is the last one — re-asking would re-serve it forever.
                if (!next.hasMore() || cursor == null) {
                    exhausted = true;
                }
            }
            return current.hasNext();
        }

        @Override
        public TurSNInventoryEntry next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return current.next();
        }
    }

    /** Chains one {@link PageWalker} per locale. */
    private class LocaleWalker implements Iterator<TurSNInventoryEntry> {
        private final String source;
        private final Iterator<String> locales;
        private Iterator<TurSNInventoryEntry> current = java.util.Collections.emptyIterator();

        LocaleWalker(String source, List<String> locales) {
            this.source = source;
            this.locales = locales.iterator();
        }

        @Override
        public boolean hasNext() {
            while (!current.hasNext() && locales.hasNext()) {
                current = new PageWalker(source, locales.next());
            }
            return current.hasNext();
        }

        @Override
        public TurSNInventoryEntry next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return current.next();
        }
    }

    /**
     * An engine that cannot enumerate its index is a failure, not an empty
     * result. Left as a page, a caller comparing against its source would find
     * every document missing.
     */
    private static TurSNInventoryPage assertSupported(TurSNInventoryPage page, String url) {
        if (!page.supported()) {
            throw new TurSNInventoryException(
                    "The search engine behind %s cannot enumerate its index, so the "
                            .formatted(url)
                            + "inventory is unavailable — this is not an empty index",
                    200);
        }
        return page;
    }

    private static void addIfPresent(URIBuilder uri, String name, String value) {
        if (value != null && !value.isBlank()) {
            uri.addParameter(name, value.trim());
        }
    }

    private static TurSNInventoryPage read(String body, String url) {
        try {
            return MAPPER.readValue(body, TurSNInventoryPage.class);
        } catch (RuntimeException e) {
            throw new TurSNInventoryException(
                    "Could not read the inventory response from " + url + ": " + e.getMessage(), e);
        }
    }

    private static String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "<no body>";
        }
        try {
            Map<?, ?> parsed = MAPPER.readValue(body, Map.class);
            Object error = parsed.get("error");
            return error == null ? body : error.toString();
        } catch (RuntimeException e) {
            return body;
        }
    }

    private static String readBody(ClassicHttpResponse response) {
        if (response.getEntity() == null) {
            return "";
        }
        try {
            return EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        } catch (IOException | ParseException e) {
            return "";
        }
    }

    private record Response(int status, String body) {
    }
}
