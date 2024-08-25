package com.viglet.turing.connector.aem.sample.ext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.connector.aem.commons.AemObject;
import com.viglet.turing.connector.aem.commons.TurAEMCommonsUtils;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.ExtContentInterface;
import com.viglet.turing.connector.aem.sample.beans.TurAemSampleModel;
import com.viglet.turing.connector.cms.beans.TurCmsTargetAttrValueMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurAemSampleModelJson implements ExtContentInterface {
    public static final String FRAGMENT_PATH = "fragmentPath";
    public static final String MODEL_JSON_EXTENSION = ".model.json";

    @Override
    public TurCmsTargetAttrValueMap consume(AemObject aemObject,
                                            TurAemSourceContext turAemSourceContext) {
        log.debug("Executing TurAemSampleModelJson");
        String url = turAemSourceContext.getUrl() + aemObject.getPath() + MODEL_JSON_EXTENSION;
        TurCmsTargetAttrValueMap attrValues = new TurCmsTargetAttrValueMap();
        return TurAEMCommonsUtils.getResponseBody(url, turAemSourceContext).map(json -> {
            ObjectMapper objectMapper = new ObjectMapper()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            if (TurCommonsUtils.isJSONValid(json)) {
                try {
                    TurAemSampleModel model = objectMapper.readValue(json, TurAemSampleModel.class);
                    getFragmentData(attrValues, model);
                    return attrValues;
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage(), e);
                }
            }
            return new TurCmsTargetAttrValueMap();
        }).orElseGet(TurCmsTargetAttrValueMap::new);
    }

    private static void getFragmentData(TurCmsTargetAttrValueMap attrValues, TurAemSampleModel model) {
        attrValues.addWithSingleValue(FRAGMENT_PATH, model.getFragmentPath(), true);
    }
}
