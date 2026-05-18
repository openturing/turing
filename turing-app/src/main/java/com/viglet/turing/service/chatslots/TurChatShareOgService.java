/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Server-side renderer for Open Graph share cards. Builds a minimal HTML
 * page with personalized {@code og:*}/{@code twitter:*} meta tags +
 * a JS redirect to the destination SPA. When a Programa-Match-style
 * deep link is shared on WhatsApp / Slack / Twitter / Discord, the
 * scrapers hit this endpoint, harvest the meta tags, and render a rich
 * preview card with the visitor's name + area + objective — instead of
 * the SPA's generic landing-page metadata.
 *
 * <p>Pattern: the flow's {@code share_url} slot points to this endpoint
 * (e.g. {@code https://education-api.example/api/sn/ee/chat/share/og?
 * name=Alexandre&area=financas&...&dest=https%3A%2F%2Fee.education.example.com
 * %2Fprograma-match}). Scrapers don't follow the redirect — they parse
 * the meta tags. Humans click the link, see the redirect, land on the
 * actual SPA.
 *
 * <p>Generic on purpose — caller passes arbitrary slot values via query
 * params; the service builds a card per a configurable template. No
 * Education-specific code lives here; the template values come from the
 * URL the chat-flow author wrote.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Slf4j
@Service
public class TurChatShareOgService {

    /**
     * Builds the OG card HTML. {@code params} carries the URL query
     * parameters (already decoded by Spring) — common keys: {@code name},
     * {@code area}, {@code objetivo}, {@code title}, {@code description},
     * {@code image}, {@code dest}. Anything else is ignored.
     *
     * <p>HTML uses no external resources — single self-contained string
     * to keep first-paint fast for the (rare) human visitor who clicks
     * before being redirected. Scrapers parse the {@code <meta>} tags
     * in {@code <head>} and never see the body.
     */
    public String renderCard(Map<String, String> params) {
        String name = orDefault(params.get("name"), "");
        String area = orDefault(params.get("area"), "");
        String objetivo = orDefault(params.get("objetivo"), "");
        // Direct overrides — caller can bypass the auto-generated title /
        // description by passing them explicitly (useful for non-Education
        // share use cases on the same endpoint).
        String title = orDefault(params.get("title"),
                buildAutoTitle(name, area));
        String description = orDefault(params.get("description"),
                buildAutoDescription(name, area, objetivo));
        String image = orDefault(params.get("image"), "");
        String dest = orDefault(params.get("dest"), "");

        StringBuilder html = new StringBuilder(1024);
        html.append("<!DOCTYPE html><html lang=\"pt-BR\"><head>\n");
        html.append("<meta charset=\"utf-8\"/>\n");
        html.append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>\n");
        html.append(metaName("title", title));
        html.append(metaName("description", description));
        // Open Graph (Facebook, WhatsApp, LinkedIn, Slack, Discord)
        html.append(metaProperty("og:type", "website"));
        html.append(metaProperty("og:title", title));
        html.append(metaProperty("og:description", description));
        if (!image.isBlank()) html.append(metaProperty("og:image", image));
        if (!dest.isBlank())  html.append(metaProperty("og:url", dest));
        html.append(metaProperty("og:locale", "pt_BR"));
        // Twitter Card (X, Discord uses these as fallback)
        html.append(metaName("twitter:card", image.isBlank() ? "summary" : "summary_large_image"));
        html.append(metaName("twitter:title", title));
        html.append(metaName("twitter:description", description));
        if (!image.isBlank()) html.append(metaName("twitter:image", image));
        // Title tag — browsers + some scrapers fall back to this when og:title
        // is missing, so emit even though we always set og:title.
        html.append("<title>").append(escapeHtml(title)).append("</title>\n");

        // Auto-redirect for humans. Scrapers don't execute meta refresh
        // (they only parse meta tags) so they get the card; visitors get
        // the SPA after 800ms. Fallback link below in case JS is off.
        if (!dest.isBlank()) {
            html.append("<meta http-equiv=\"refresh\" content=\"0;url=")
                    .append(escapeAttr(dest)).append("\"/>\n");
        }
        html.append("</head><body style=\"font-family:system-ui;margin:40px;color:#1f2937;\">\n");
        html.append("<p>").append(escapeHtml(title)).append("</p>\n");
        if (!dest.isBlank()) {
            html.append("<p><a href=\"").append(escapeAttr(dest))
                    .append("\">Continuar para Executive Education →</a></p>\n");
            // JS fallback for the meta refresh — fires immediately
            html.append("<script>setTimeout(function(){location.replace(\"")
                    .append(escapeJs(dest)).append("\")},100);</script>\n");
        }
        html.append("</body></html>");
        return html.toString();
    }

    /** "Plano de carreira de Alexandre — Finanças" */
    private static String buildAutoTitle(String name, String area) {
        String base = "Plano de carreira";
        if (!name.isBlank()) base += " de " + name;
        if (!area.isBlank()) base += " — " + capitalize(area);
        return base;
    }

    /**
     * "Alexandre escolheu a trilha de Finanças no Programa-Match da Executive Education,
     *  com o objetivo de virar CFO em 3 anos. Veja a recomendação personalizada."
     */
    private static String buildAutoDescription(String name, String area, String objetivo) {
        StringBuilder sb = new StringBuilder();
        if (!name.isBlank()) sb.append(name);
        else sb.append("Um(a) visitante");
        if (!area.isBlank()) sb.append(" escolheu a trilha de ").append(capitalize(area));
        sb.append(" no Programa-Match da Executive Education");
        if (!objetivo.isBlank()) sb.append(", com o objetivo de ").append(objetivo);
        sb.append(". Veja a recomendação personalizada.");
        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String metaName(String name, String content) {
        return "<meta name=\"" + escapeAttr(name) + "\" content=\""
                + escapeAttr(content) + "\"/>\n";
    }

    private static String metaProperty(String property, String content) {
        return "<meta property=\"" + escapeAttr(property) + "\" content=\""
                + escapeAttr(content) + "\"/>\n";
    }

    /** HTML body escape — text nodes. */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Attribute escape — adds quote handling on top of HTML escape. */
    private static String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }

    /** JS string literal escape — used inside the redirect script. */
    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("</", "<\\/");
    }
}
