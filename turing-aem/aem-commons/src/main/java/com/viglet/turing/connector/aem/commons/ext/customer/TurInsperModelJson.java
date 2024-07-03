package com.viglet.turing.connector.aem.commons.ext.customer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.Criteria;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.Filter;
import com.jayway.jsonpath.JsonPath;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.bean.customer.*;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtContentInterface;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueList;
import com.viglet.turing.connector.cms.util.HtmlManipulator;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class TurInsperModelJson implements ExtContentInterface {
    public static final String ABSTRACT = "abstract";
    public static final String TEMPLATE_NAME = "templateName";
    public static final String FRAGMENT_PATH = "fragmentPath";
    public static final String MODEL_JSON_EXTENSION = ".model.json";
    public static final String HTML_EXTENSION = ".html";
    public static final String DATE = "date";
    public static final String HOUR = "hour";
    public static final String END_DATE = "endDate";
    public static final String END_HOUR = "endHour";
    public static final String EVENT_TEXT = "eventText";
    public static final String IMAGE = "image";
    public static final String BACKGROUND_IMAGE = "backgroundImage";
    public static final String BACKGROUND_IMAGE_COLOR = "backgroundImageColor";
    public static final String RICH_TEXT = "richText";
    public static final String MODIFICATION_DATE = "modificationDate";
    public static final String TITLE = "title";
    public static final String SLING_RESOURCE_TYPE = ":type";
    public static final String INSPER_PORTAL_COMPONENTS_EVENT_DETAILS = "insper-portal/components/eventDetails";
    public static final String INSPER_PORTAL_COMPONENTS_TEXT = "insper-portal/components/text";
    public static final String NOVA_NOTICIA = "nova-noticia";
    public static final String INSPER_CONHECIMENTO = "insper-conhecimento";
    public static final String WEBINAR_TEMPLATE = "webinar-template";
    public static final String NOTICIAS = "noticias";
    public static final String EVENTO = "evento";
    public static final String CONTATO = "contato";
    public static final String HOME_DOS_EVENTOS = "home-dos-eventos";
    public static final String SUB_HOME_DE_CURSOS = "sub-home-de-cursos";
    public static final String SUB_HOME_INSTITUCIONAL = "sub-home-institucional";
    public static final String SUB_HOME_DE_CURSOS_COM_BUSCA = "sub-home-de-cursos-com-busca";
    public static final String INSTITUCIONAL = "institucional";

    @Override
    public TurCmsTargetAttrValueList consume(AemObject aemObject,
                                             TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurInsperModelJson");
        TurCmsTargetAttrValueList attrValues = new TurCmsTargetAttrValueList();
        try (HttpClient client = HttpClient.newBuilder()
                .authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(turAemSourceContext.getUsername(),
                                turAemSourceContext.getPassword().toCharArray());
                    }
                })
                .build()) {
            String url = String.format("%s%s.html", turAemSourceContext.getUrl(), aemObject.getPath()).replaceAll(HTML_EXTENSION + "$",
                    MODEL_JSON_EXTENSION);
            HttpRequest request = HttpRequest.newBuilder().GET().uri(new URI(url)).build();
            String json = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            ObjectMapper objectMapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            if (isValid(json)) {
                TurInsperModel model = objectMapper.readValue(json, TurInsperModel.class);
                getFragmentData(attrValues, model);
                getResponsiveGrid(model, attrValues);
                getEvent(json, objectMapper, attrValues);
                return attrValues;
            }
        } catch (URISyntaxException | InterruptedException | IOException e) {
            log.error(e.getMessage(), e);
        }
        return new TurCmsTargetAttrValueList();
    }

    private static void getResponsiveGrid(TurInsperModel model, TurCmsTargetAttrValueList attrValues) {
        Optional.ofNullable(model.getItems())
                .map(TurInsperModelItems::getRoot)
                .map(TurInsperModelRoot::getRootItems)
                .ifPresent(rootItem ->
                        rootItem.values().forEach(turInsperResponsiveGrid ->
                                Optional.ofNullable(turInsperResponsiveGrid)
                                        .map(TurInsperResponsiveGrid::getItems)
                                        .ifPresent(items -> {
                                            getGenericBanner(items, attrValues);
                                            getTeacher(items, attrValues);
                                        })));
    }

    private static void getFragmentData(TurCmsTargetAttrValueList attrValues, TurInsperModel model) {
        attrValues.addWithSingleValue(FRAGMENT_PATH, model.getFragmentPath());
        attrValues.addWithSingleValue(TEMPLATE_NAME, setTemplateName(model));
        Optional.ofNullable(model.getGenericContentFragmentData()).ifPresent(fragmentData -> {
            attrValues.addWithSingleValue(ABSTRACT, Jsoup.parse(fragmentData.getDescription()).text());
            attrValues.addWithSingleValue(MODIFICATION_DATE, fragmentData.getDisplayDate());
            attrValues.addWithSingleValue(IMAGE, fragmentData.getImage());
            attrValues.addWithSingleValue(DATE, fragmentData.getInitialDate());
            attrValues.addWithSingleValue(HOUR, fragmentData.getInitialHour());
        });
    }

    private static String setTemplateName(TurInsperModel model) {
        return switch (model.getTemplateName()) {
            case CONTATO, HOME_DOS_EVENTOS, SUB_HOME_DE_CURSOS, SUB_HOME_INSTITUCIONAL,
                 SUB_HOME_DE_CURSOS_COM_BUSCA -> INSTITUCIONAL;
            case NOVA_NOTICIA, INSPER_CONHECIMENTO -> NOTICIAS;
            case WEBINAR_TEMPLATE -> EVENTO;
            default -> model.getTemplateName();
        };
    }

    private static void getGenericBanner(TurInsperResponsiveGridItems items, TurCmsTargetAttrValueList attrValues) {
        Optional.ofNullable(items.getGenericBanner())
                .map(TurInsperGenericBanner::getItems)
                .map(TurInsperGenericBannerItems::getGenericBannerItem)
                .ifPresent(bannerItem -> {
                    attrValues.addWithSingleValue(MODIFICATION_DATE,
                            bannerItem.getAuthorDate());
                    attrValues.addWithSingleValue(BACKGROUND_IMAGE,
                            bannerItem.getBackgroundImage());
                    attrValues.addWithSingleValue(BACKGROUND_IMAGE_COLOR,
                            bannerItem.getBackgroundImageColor());
                    attrValues.addWithSingleValue(RICH_TEXT,
                            bannerItem.getRichText());
                });
    }

    private static void getTeacher(TurInsperResponsiveGridItems items, TurCmsTargetAttrValueList attrValues) {
        Optional.ofNullable(items.getTeacher())
                .map(TurInsperTeacher::getElements)
                .ifPresent(elements -> {
                    Optional.ofNullable(elements.getNomeCompleto())
                            .ifPresent(fullName ->
                                    attrValues.addWithSingleValue(TITLE,
                                            fullName.getValue()));
                    Optional.ofNullable(elements.getTitulacao())
                            .ifPresent(titration ->
                                    attrValues.addWithSingleValue(ABSTRACT,
                                            titration.getValue()));
                    Optional.ofNullable(elements.getFoto())
                            .ifPresent(photo ->
                                    attrValues.addWithSingleValue(IMAGE,
                                            photo.getValue()));
                });
    }

    private static void getEvent(String json, ObjectMapper objectMapper, TurCmsTargetAttrValueList attrValues) {
        DocumentContext jsonContext = JsonPath.parse(json);
        Object jsonDetails = jsonContext.read("$..[?]",
                Filter.filter(Criteria
                        .where(SLING_RESOURCE_TYPE)
                        .eq(INSPER_PORTAL_COMPONENTS_EVENT_DETAILS)));
        List<TurInsperModelEventDetails> eventDetails = objectMapper
                .convertValue(jsonDetails, new TypeReference<>() {
                });
        eventDetails.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .ifPresent(d -> {
                    attrValues.addWithSingleValue(DATE, d.getDate());
                    attrValues.addWithSingleValue(HOUR, d.getHour());
                    attrValues.addWithSingleValue(END_DATE, d.getEndDate());
                    attrValues.addWithSingleValue(END_HOUR, d.getEndHour());
                    getEventDescription(jsonContext, objectMapper, attrValues);
                });
    }

    private static void getEventDescription(DocumentContext jsonContext, ObjectMapper objectMapper,
                                            TurCmsTargetAttrValueList attrValues) {
        Object jsonDescription = jsonContext.read("$..[?]",
                Filter.filter(Criteria
                        .where(SLING_RESOURCE_TYPE)
                        .eq(INSPER_PORTAL_COMPONENTS_TEXT)));
        List<TurInsperModelText> eventDescription = objectMapper
                .convertValue(jsonDescription, new TypeReference<>() {
                });
        eventDescription.stream()
                .filter(description -> description != null && description.getText() != null)
                .findFirst()
                .ifPresent(d ->
                        attrValues.addWithSingleValue(EVENT_TEXT,
                                HtmlManipulator.html2Text(d.getText())));
    }

    public boolean isValid(String json) {
        try {
            new JSONObject(json);
        } catch (JSONException e) {
            try {
                new JSONArray(json);
            } catch (JSONException ne) {
                return false;
            }
        }
        return true;
    }

}
