package com.viglet.turing.connector.aem.sample.ext;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtContentInterface;
import com.viglet.turing.connector.aem.sample.beans.TurAemSampleModel;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
public class TurAemSampleModelJson implements ExtContentInterface {
    public static final String FRAGMENT_PATH = "fragmentPath";
    public static final String MODEL_JSON_EXTENSION = ".model.json";
    public static final String HTML_EXTENSION = ".html";

    @Override
    public TurCmsTargetAttrValueMap consume(AemObject aemObject,
                                            TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurAemSampleModelJson");
        TurCmsTargetAttrValueMap attrValues = new TurCmsTargetAttrValueMap();
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
                    MODEL_JSON_EXTENSION);
            HttpRequest request = HttpRequest.newBuilder().GET().uri(new URI(url)).build();
            String json = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            ObjectMapper objectMapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            if (TurCommonsUtils.isJSONValid(json)) {
                TurAemSampleModel model = objectMapper.readValue(json, TurAemSampleModel.class);
                getFragmentData(attrValues, model);
                return attrValues;
            }
        } catch (URISyntaxException | IOException e) {
            log.error(e.getMessage(), e);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
        return new TurCmsTargetAttrValueMap();
    }

    private static void getFragmentData(TurCmsTargetAttrValueMap attrValues, TurAemSampleModel model) {
        attrValues.addWithSingleValue(FRAGMENT_PATH, model.getFragmentPath(), true);
    }
}
