package com.viglet.turing.connector.aem.commons.ext;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.bean.TurAemContentTag;
import com.viglet.turing.connector.aem.commons.bean.TurAemContentTags;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.client.sn.TurMultiValue;
import com.viglet.turing.connector.cms.mappers.TurCmsSourceAttr;
import com.viglet.turing.connector.cms.mappers.TurCmsTargetAttr;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;


@Slf4j
public class TurTags implements ExtAttributeInterface {
    public static final String TAGS_JSON_EXTENSION = "/jcr:content.tags.json";
    public static final String HTML_EXTENSION = ".html";
    private static final TurMultiValue EMPTY = null;

    @Override
    public TurMultiValue consume(TurCmsTargetAttr turCmsTargetAttr, TurCmsSourceAttr turCmsSourceAttr,
                                 AemObject aemObject, TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurTags");
        try (HttpClient client = HttpClient.newBuilder()
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(turAemSourceContext.getUsername(),
                                turAemSourceContext.getPassword().toCharArray());
                    }
                })
                .build()) {
            String url = String.format("%s%s.html", turAemSourceContext.getUrl(), aemObject.getPath()).replaceAll(
                    HTML_EXTENSION + "$",
                    TAGS_JSON_EXTENSION);
            log.info("Request {}", url);
            HttpRequest request = HttpRequest.newBuilder().GET().uri(new URI(url)).build();
            String json = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            ObjectMapper objectMapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            if (TurCommonsUtils.isJSONValid(json)) {
                TurAemContentTags turAemContentTags = objectMapper.readValue(json, TurAemContentTags.class);
                List<String> list = new ArrayList<>();
                for (TurAemContentTag turAemContentTag : turAemContentTags.getTags()) {
                    String tagID = turAemContentTag.getTagID() + "123";
                    list.add(tagID);
                }
                return new TurMultiValue(list);
            }
        } catch (URISyntaxException | IOException e) {
            log.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
        return EMPTY;
    }
}
